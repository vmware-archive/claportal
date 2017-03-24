/**
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package controllers;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlUpdate;
import com.avaje.ebean.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import it.innove.play.pdf.PdfGenerator;
import models.Cla;
import models.ClaInputField;
import models.Dco;
import models.Organization;
import models.ProjectCla;
import models.ProjectDco;
import models.SignedCla;
import models.SignedClaGitHubPullRequest;
import models.SignedClaInputField;
import play.Logger;
import play.Play;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.libs.mailer.Email;
import play.libs.mailer.MailerPlugin;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import utils.GitHubApiUtils;
import views.html.claEmail;
import views.html.claPdf;
import views.html.dcoPreview;
import views.html.externalReviewEmail;
import views.html.index;
import views.html.message;
import views.html.reviewEmail;

public class ClaController extends Controller {
    public static final String CLA_REJECTED_LABEL = "cla-rejected";
    public static final String CLA_NOT_REQUIRED_LABEL = "cla-not-required";
    public static final String DCO_REQUIRED = "dco-required";
    public static final String DCO_NOT_REQUIRED_LABEL = "dco-not-required";

    private static SignedCla getSignedCla(String uuid) throws ResultException {
        SignedCla cla = SignedCla.find.where().eq("uuid", uuid).findUnique();
        if (cla == null) {
            throw new ResultException(notFound(message.render("The contributor license agreement does not exist")));
        }
        if (!cla.getState().equals(SignedCla.STATE_NEW) && !cla.getState().equals(SignedCla.STATE_REJECTED)
                && !cla.getState().equals(SignedCla.STATE_PENDING_EXTERNAL)) {
            throw new ResultException(badRequest(message.render("The contributor license agreement has already been signed")));
        }
        return cla;
    }

    public static String getField(String key, Map<String, String[]> fields, int maxLength) {
        String[] values = fields.get(key);
        if (values == null || values.length == 0) {
            throw new IllegalStateException(key + " can not be missing");
        }
        String value = StringEscapeUtils.unescapeHtml4(values[0].trim());
        if (maxLength > 0) {
            value = value.substring(0, Math.min(value.length(), maxLength));
        }
        return value;
    }

    public static String getToken(String uuid, String email) {
        StringBuilder builder = new StringBuilder();
        builder.append(uuid);
        builder.append(email);
        builder.append(Play.application().configuration().getString("application.secret"));
        return DigestUtils.sha256Hex(builder.toString());
    }

    public static Result dco() {
        Dco dco = Dco.find.orderBy("revision DESC").setMaxRows(1).findUnique();
        if (dco == null) {
            return notFound(message.render("The developer certificate of origin request does not exist"));
        }
        return ok(dcoPreview.render(dco));
    }

    public static Result index(String uuid) {
        session().clear();
        SignedCla cla = null;
        try {
            cla = getSignedCla(uuid);
        } catch (ResultException e) {
            return e.getResult();
        }
        String redirectUrl = Play.application().configuration().getString("app.host");
        redirectUrl += controllers.routes.ClaController.authCallback(uuid, null).url();
        redirectUrl = redirectUrl.substring(0, redirectUrl.indexOf("?"));
        StringBuilder builder = new StringBuilder();
        builder.append("https://github.com/login/oauth/authorize/?client_id=");
        builder.append(Play.application().configuration().getString("app.github.clientid"));
        builder.append("&redirect_uri=");
        try {
            builder.append(URLEncoder.encode(redirectUrl, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            /* Should never happen */
            return internalServerError();
        }
        builder.append("&scope=user:email");
        String authUrl = builder.toString();
        return ok(index.render(cla, authUrl));
    }

    public static Result authCallback(String uuid, String code) {
        SignedCla cla = null;
        try {
            cla = getSignedCla(uuid);
        } catch (ResultException e) {
            return e.getResult();
        }
        WSRequestHolder holder = WS.url("https://github.com/login/oauth/access_token");
        holder = holder.setQueryParameter("client_id", Play.application().configuration().getString("app.github.clientid"));
        holder = holder.setQueryParameter("client_secret",
                Play.application().configuration().getString("app.github.clientsecret"));
        holder = holder.setQueryParameter("code", code);
        holder = holder.setHeader("Accept", "application/json");
        Promise<WSResponse> response = holder.post("");
        JsonNode json = response.get(30000).asJson();
        String token = json.get("access_token").asText();
        String header = GitHubApiUtils.getAuthHeader(token);
        response = WS.url("https://api.github.com/user").setHeader("Authorization", header).get();
        json = response.get(30000).asJson();
        String uid = json.get("id").asText();
        if (!uid.equals(cla.getGitHubUid())) {
            return unauthorized(message.render("This contributor license agreement request does not belong to you"));
        }
        List<String> emails = new ArrayList<String>();
        response = WS.url("https://api.github.com/user/emails").setHeader("Authorization", header).get();
        json = response.get(30000).asJson();
        if (json.isArray()) {
            ArrayNode array = (ArrayNode) json;
            Iterator<JsonNode> iterator = array.iterator();
            while (iterator.hasNext()) {
                JsonNode node = iterator.next();
                emails.add(node.get("email").asText());
            }
        }
        if (emails.isEmpty()) {
            return badRequest(message.render("Unable to get e-mail address from GitHub for " + cla.getGitHubLogin()));
        }
        boolean employerCheck = cla.getLegalContactEmail() != null;
        Map<Long, String> responses = new HashMap<Long, String>();
        for (SignedClaInputField field : cla.getInputFields()) {
            responses.put(field.getInputField().getId(), field.getResponse());
        }
        session().clear();
        session("login", cla.getGitHubUid());
        String expiration = Play.application().configuration().getString("app.ccla.expiration");
        return ok(views.html.cla.render(emails, cla, employerCheck, responses, expiration));
    }

    @Transactional
    public static Result signCla(String uuid) {
        String uid = session("login");
        if (uid == null) {
            return forbidden(message.render("You must be signed in to access this page"));
        }
        SignedCla cla = null;
        try {
            cla = getSignedCla(uuid);
        } catch (ResultException e) {
            return e.getResult();
        }
        if (!uid.equals(cla.getGitHubUid())) {
            session().clear();
            return unauthorized(message.render("This contributor license agreement request does not belong to you"));
        }
        cla.setState(SignedCla.STATE_PENDING);
        cla.setUpdateComment(null);
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        boolean employerChecked = false;
        try {
            employerChecked = values.containsKey("employerCheck");
            String email = getField("email", values, 128);
            String signature = getField("signature", values, 0);
            cla.setEmail(email);
            cla.setSignature(signature);
            if (employerChecked) {
                cla.setState(SignedCla.STATE_PENDING_EXTERNAL);
                String legalContactEmail = getField("legalContactEmail", values, 128);
                if (email.equals(legalContactEmail)) {
                    return badRequest(message.render("The legal contact e-mail can not match the selected e-mail"));
                }
                cla.setLegalContactEmail(legalContactEmail);
                cla.setLegalState(SignedCla.STATE_PENDING);
            } else {
                cla.setLegalState(null);
                cla.setLegalContactEmail(null);
            }
            List<ClaInputField> fields = cla.getCla().getInputFields();
            Map<Long, Boolean> checkMap = new HashMap<Long, Boolean>();
            for (ClaInputField field : fields) {
                if (field.getInputField().getRequiredForEmployer()) {
                    if (employerChecked) {
                        checkMap.put(field.getInputField().getId(), false);
                    }
                } else {
                    checkMap.put(field.getInputField().getId(), false);
                }
            }
            /* If this is a re-sign, delete the old values */
            SqlUpdate sql = Ebean.createSqlUpdate("DELETE FROM SignedClaInputFields WHERE signedClaId = :id");
            sql.setParameter("id", cla.getId());
            sql.execute();
            List<SignedClaInputField> signedFields = new ArrayList<SignedClaInputField>();
            for (String key : values.keySet()) {
                if (key.startsWith("field")) {
                    int id = Integer.parseInt(key.replace("field", ""));
                    for (ClaInputField field : fields) {
                        if (field.getInputField().getId().intValue() == id) {
                            SignedClaInputField signedField = new SignedClaInputField();
                            signedField.setInputField(field.getInputField());
                            signedField.setSignedCla(cla);
                            signedField.setResponse(getField(key, values, 128));
                            signedFields.add(signedField);
                            checkMap.put(field.getInputField().getId(), true);
                        }
                    }
                }
            }
            cla.setInputFields(signedFields);
            for (Boolean hasField : checkMap.values()) {
                if (!hasField) {
                    return badRequest(message.render("Required field missing"));
                }
            }
        } catch (IllegalStateException e) {
            return badRequest(message.render(e.getMessage()));
        } catch (NumberFormatException e) {
            return badRequest(message.render("Invalid field ID"));
        }
        Ebean.save(cla);

        String status = "success";
        String statusMessage = Messages.get("github.status.review");
        if (employerChecked) {
            status = "pending";
            statusMessage = Messages.get("github.status.reviewexternal");
        }
        for (SignedClaGitHubPullRequest pullRequest : cla.getPullRequests()) {
            GitHubApiUtils.updateGitHubStatus(cla.getUuid(), status, statusMessage, pullRequest.getGitHubStatusUrl());
        }

        /* E-mail to signer */
        String body = claEmail.render(cla.getProject(), cla.getGitHubLogin(), cla.getEmail()).body();
        Email email = new Email();
        email.setSubject("New CLA Signed");
        email.setFrom(Play.application().configuration().getString("app.noreply.email"));
        email.addTo(cla.getEmail());
        email.setBodyHtml(body);
        String name = "SignedCla_" + cla.getProject() + "_" + cla.getGitHubLogin() + "_"
                + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".pdf";
        byte[] pdf = PdfGenerator.toBytes(claPdf.render(cla), "");
        email.addAttachment(name, pdf, "application/pdf");
        MailerPlugin.send(email);

        String confirmation = Messages.get("sign.confirmation");
        if (employerChecked) {
            /* E-mail to external legal contact */
            confirmation = Messages.get("sign.confirmation.ccla",
                    Play.application().configuration().getString("app.ccla.expiration"));
            String token = getToken(uuid, cla.getLegalContactEmail());
            String url = Play.application().configuration().getString("app.host")
                    + controllers.routes.ExternalReviewController.review(uuid, token).url();
            body = externalReviewEmail.render(url, cla.getProject(), cla.getGitHubLogin(), cla.getEmail()).body();
            email = new Email();
            email.setSubject("Open Source Contributor License Agreement for " + cla.getProject() + " needs your review");
            email.setFrom(Play.application().configuration().getString("app.noreply.email"));
            email.addTo(cla.getLegalContactEmail());
            email.setBodyHtml(body);
            MailerPlugin.send(email);
        } else {
            /* E-mail to internal reviewers */
            String url = Play.application().configuration().getString("app.internal.host")
                    + controllers.routes.AdminController.review(uuid).url();
            body = reviewEmail.render(url, cla.getProject(), cla.getGitHubLogin(), cla.getEmail()).body();
            email = new Email();
            email.setSubject("New CLA Signed");
            email.setFrom(Play.application().configuration().getString("app.noreply.email"));
            email.addTo(Play.application().configuration().getString("app.notification.email"));
            email.setBodyHtml(body);
            MailerPlugin.send(email);
        }
        session().clear();
        return ok(message.render(confirmation));
    }

    private static void handleDco(String login, String pullRequestUrl, String issueUrl, String repoUrl) {
        String dcoUrl = GitHubApiUtils.formatLink("here",
                Play.application().configuration().getString("app.host") + controllers.routes.ClaController.dco());
        String comment = Messages.get("github.issue.dco", login, dcoUrl);
        String url = pullRequestUrl + "/commits";
        String header = GitHubApiUtils.getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(url).setHeader("Authorization", header).get();
        WSResponse wsResponse = response.get(30000);
        JsonNode json = wsResponse.asJson();
        if (json.isArray()) {
            ArrayNode array = (ArrayNode) json;
            Iterator<JsonNode> iterator = array.iterator();
            boolean addDcoReminder = false;
            StringBuilder builder = new StringBuilder();
            while (iterator.hasNext()) {
                JsonNode node = iterator.next();
                JsonNode commitNode = node.get("commit");
                String sha = node.get("sha").asText();
                String message = commitNode.get("message").asText().trim();
                String email = commitNode.get("author").get("email").asText();
                boolean foundEmail = false;
                Scanner scanner = new Scanner(message);
                try {
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine().toLowerCase();
                        if (line.startsWith("signed-off-by:")) {
                            int emailStart = line.lastIndexOf("<");
                            int emailEnd = line.lastIndexOf(">");
                            if (emailStart > 0 && emailEnd > 0) {
                                String commitEmail = line.substring(emailStart + 1, emailEnd);
                                if (email.equals(commitEmail)) {
                                    foundEmail = true;
                                    break;
                                }
                            }
                        }
                    }
                } finally {
                    scanner.close();
                }
                if (!foundEmail) {
                    builder.append("- Commit ");
                    builder.append(sha);
                    builder.append(" must be signed by ");
                    builder.append(email);
                    builder.append("\n");
                    addDcoReminder = true;
                }
            }
            if (addDcoReminder) {
                comment += "\n\n" + builder.toString();
                GitHubApiUtils.addIssueComment(comment, issueUrl + "/comments");
                GitHubApiUtils.addIssueLabel(repoUrl, DCO_REQUIRED, "fc2929");
                GitHubApiUtils.attachIssueLabel(issueUrl + "/labels", DCO_REQUIRED);
                return;
            } else {
                String labelUrl = issueUrl + "/labels/" + DCO_REQUIRED;
                GitHubApiUtils.removeIssueLabel(labelUrl);
            }
        }
    }

    private static boolean isInRange(SignedCla signedCla, ProjectCla projectCla) {
        int revision = signedCla.getCla().getRevision();
        int minRevision = projectCla.getMinCla().getRevision();
        int maxRevision = projectCla.getMaxCla().getRevision();
        return revision >= minRevision && revision <= maxRevision;
    }

    private static boolean hasPullRequest(String pullRequestUrl, List<SignedClaGitHubPullRequest> pullRequests) {
        for (SignedClaGitHubPullRequest pullRequest : pullRequests) {
            if (pullRequestUrl.equals(pullRequest.getGitHubPullRequestUrl())) {
                return true;
            }
        }
        return false;
    }

    public static void handleCla(SignedCla cla, ProjectCla projectCla, String uid, String login, String pullRequestUrl,
            String statusUrl, String issueUrl, String repoUrl) {
        String state = null;
        String description = null;
        String comment = null;
        if (cla == null) {
            state = "pending";
            description = Messages.get("github.status.sign");
            cla = new SignedCla();
            cla.setUuid(UUID.randomUUID().toString().replace("-", ""));
            cla.setCla(projectCla.getMaxCla());
            cla.setProject(projectCla.getProject());
            cla.setGitHubUid(uid);
            cla.setGitHubLogin(login);
            cla.setPullRequests(new ArrayList<SignedClaGitHubPullRequest>());
            cla.setState(SignedCla.STATE_NEW);
            cla.setCreated(new Date());
            String signUrl = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                    + controllers.routes.ClaController.index(cla.getUuid()).url());
            comment = Messages.get("github.issue.sign", login, signUrl);
        } else if (!isInRange(cla, projectCla)) {
            state = "pending";
            description = Messages.get("github.status.outdated");
            cla = new SignedCla();
            cla.setUuid(UUID.randomUUID().toString().replace("-", ""));
            cla.setCla(projectCla.getMaxCla());
            cla.setProject(projectCla.getProject());
            cla.setGitHubUid(uid);
            cla.setGitHubLogin(login);
            cla.setPullRequests(new ArrayList<SignedClaGitHubPullRequest>());
            cla.setState(SignedCla.STATE_NEW);
            cla.setCreated(new Date());
            String signUrl = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                    + controllers.routes.ClaController.index(cla.getUuid()).url());
            comment = Messages.get("github.issue.outdated", login, signUrl);
        } else if (cla.getState().equals(SignedCla.STATE_NEW)) {
            state = "pending";
            description = Messages.get("github.status.sign");
            String signUrl = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                    + controllers.routes.ClaController.index(cla.getUuid()).url());
            comment = Messages.get("github.issue.sign", login, signUrl);
        } else if (cla.getState().equals(SignedCla.STATE_PENDING)) {
            state = "success";
            description = Messages.get("github.status.review");
        } else if (cla.getState().equals(SignedCla.STATE_PENDING_EXTERNAL)) {
            state = "pending";
            description = Messages.get("github.status.reviewexternal");
        } else if (cla.getState().equals(SignedCla.STATE_APPROVED)) {
            state = "success";
            description = Messages.get("github.status.approved");
        } else if (cla.getState().equals(SignedCla.STATE_REJECTED)) {
            state = "failure";
            description = Messages.get("github.status.rejected");
            String signUrl = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                    + controllers.routes.ClaController.index(cla.getUuid()).url());
            comment = Messages.get("github.issue.rejected", login, signUrl);
        }
        if (!hasPullRequest(pullRequestUrl, cla.getPullRequests())) {
            SignedClaGitHubPullRequest pullRequest = new SignedClaGitHubPullRequest();
            pullRequest.setGitHubPullRequestUrl(pullRequestUrl);
            pullRequest.setGitHubStatusUrl(statusUrl);
            pullRequest.setGitHubIssueUrl(issueUrl);
            cla.getPullRequests().add(pullRequest);
        }
        Ebean.save(cla);
        GitHubApiUtils.updateGitHubStatus(cla.getUuid(), state, description, statusUrl);
        GitHubApiUtils.addIssueLabel(repoUrl, CLA_REJECTED_LABEL, "fc2929");
        if (comment != null) {
            GitHubApiUtils.addIssueComment(comment, issueUrl + "/comments");
        }
    }

    @Transactional
    public static Result pullRequestHookCallback() {
        JsonNode json = request().body().asJson();
        if (json == null) {
            return ok();
        }
        if (!json.has("action")) {
            return ok();
        }
        String action = json.get("action").asText();
        if (action.equals("opened") || action.equals("synchronize")) {
            JsonNode pullRequestNode = json.get("pull_request");
            JsonNode userNode = pullRequestNode.get("user");
            JsonNode repositoryNode = json.get("repository");
            JsonNode ownerNode = repositoryNode.get("owner");
            String uid = userNode.get("id").asText();
            String login = userNode.get("login").asText();
            String pullRequestUrl = pullRequestNode.get("url").asText();
            String statusUrl = pullRequestNode.get("statuses_url").asText();
            String issueUrl = pullRequestNode.get("issue_url").asText();
            String repo = repositoryNode.get("name").asText();
            String repoUrl = repositoryNode.get("url").asText();
            String owner = ownerNode.get("login").asText();
            Logger.info("Pull request " + pullRequestUrl + " opened/synchronized");

            /* Reject hook callbacks from other organizations */
            boolean foundOrg = false;
            List<Organization> orgs = Organization.find.findList();
            for (Organization org : orgs) {
                if (org.getName().equals(owner)) {
                    foundOrg = true;
                    break;
                }
            }
            if (!foundOrg) {
                return ok();
            }

            boolean hasDco = ProjectDco.find.where().eq("project", owner + "/" + repo).findUnique() != null;
            if (hasDco) {
                handleDco(login, pullRequestUrl, issueUrl, repoUrl);
            } else if (action.equals("opened")) {
                /* Collaborators do not need to sign the CLA */
                Set<String> collaborators = GitHubApiUtils.getCollaborators(owner, repo);
                if (collaborators.contains(login)) {
                    GitHubApiUtils.addIssueLabel(repoUrl, CLA_NOT_REQUIRED_LABEL, "159818");
                    GitHubApiUtils.attachIssueLabel(issueUrl + "/labels", CLA_NOT_REQUIRED_LABEL);
                    String status = Messages.get("github.status.notrequired");
                    GitHubApiUtils.updateGitHubStatus("success", status, statusUrl);
                    return ok();
                }

                SignedCla cla = null;
                ProjectCla projectCla = ProjectCla.find.where().eq("project", owner + "/" + repo).findUnique();
                if (projectCla == null) {
                    Cla defaultCla = Cla.find.where().eq("isDefault", true).findUnique();
                    projectCla = new ProjectCla();
                    projectCla.setMinCla(defaultCla);
                    projectCla.setMaxCla(defaultCla);
                    projectCla.setProject(owner + "/" + repo);
                    cla = SignedCla.find.setForUpdate(true).where().eq("claId", defaultCla.getId()).eq("gitHubUid", uid)
                            .ne("state", SignedCla.STATE_REVOKED).findUnique();
                } else {
                    List<Cla> clas = Cla.find.where().eq("name", projectCla.getMinCla().getName()).findList();
                    for (Cla c : clas) {
                        cla = SignedCla.find.setForUpdate(true).where().eq("claId", c.getId()).eq("gitHubUid", uid)
                                .ne("state", SignedCla.STATE_REVOKED).findUnique();
                        if (cla != null) {
                            if (isInRange(cla, projectCla)) {
                                break;
                            } else {
                                cla = null;
                            }
                        }
                    }
                }
                handleCla(cla, projectCla, uid, login, pullRequestUrl, statusUrl, issueUrl, repoUrl);
            }
        }
        return ok();
    }
}
