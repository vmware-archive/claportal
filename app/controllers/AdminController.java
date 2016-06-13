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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ocpsoft.prettytime.PrettyTime;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.SqlUpdate;
import com.avaje.ebean.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import models.Admin;
import models.Cla;
import models.InputField;
import models.InstalledWebhook;
import models.Organization;
import models.ProjectCla;
import models.Review;
import models.SignedCla;
import models.SignedClaGitHubPullRequest;
import play.Logger;
import play.Play;
import play.Routes;
import play.i18n.Messages;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import play.mvc.Security;
import utils.GitHubApiUtils;
import views.html.admins;
import views.html.defaultCla;
import views.html.editor;
import views.html.inputFields;
import views.html.login;
import views.html.manage;
import views.html.manageProjects;
import views.html.mapCla;
import views.html.message;
import views.html.newInputField;
import views.html.review;
import views.html.search;

public class AdminController extends Controller {
    public static Result login(String next) {
        return ok(login.render(next, null));
    }

    public static Result doLogin() {
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        String next = null;
        if (values.containsKey("next")) {
            next = ClaController.getField("next", values, 0);
        }
        String user = null;
        String password = null;
        try {
            user = ClaController.getField("user", values, 0);
            password = ClaController.getField("password", values, 0);
        } catch (IllegalStateException e) {
            return ok(login.render(next, e.getMessage()));
        }
        if (Admin.find.where().eq("admin", user).findUnique() == null) {
            return ok(login.render(next, "This user is not an administrator"));
        }
        if (!Authenticator.authenticate(user, password)) {
            return ok(login.render(next, "Invalid credentials"));
        }
        session().clear();
        session().put("admin", user);
        if (next == null) {
            return redirect(controllers.routes.AdminController.search());
        } else {
            return redirect(next);
        }
    }

    public static Result logout() {
        session().clear();
        return redirect(controllers.routes.AdminController.login(null));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result newCla() {
        List<InputField> fields = InputField.find.findList();
        return ok(editor.render(null, null, null, null, fields, null, null));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result editCla(String name) {
        List<Cla> clas = Cla.find.where().eq("name", name).orderBy("revision DESC").findList();
        String markdown = null;
        String claJson = null;
        if (!clas.isEmpty()) {
            markdown = clas.iterator().next().getText();
            ObjectNode json = Json.newObject();
            for (Cla cla : clas) {
                json.put(Integer.toString(cla.getRevision()), Json.toJson(cla));
            }
            claJson = json.toString();
        }
        List<InputField> fields = InputField.find.findList();
        return ok(editor.render(clas, name, markdown, claJson, fields, null, null));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result ajaxCheckClaName(String name) {
        List<Cla> clas = Cla.find.where().eq("name", name).findList();
        return ok(Boolean.toString(!clas.isEmpty()));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result updateCla() {
        List<InputField> fields = InputField.find.findList();
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        String name = null;
        String markdown = null;
        try {
            name = ClaController.getField("name", values, 64);
            markdown = ClaController.getField("markdown", values, 0);
        } catch (IllegalStateException e) {
            return ok(editor.render(null, null, null, null, fields, null, e.getMessage()));
        }
        Map<Integer, Long> ids = new TreeMap<Integer, Long>();
        for (String key : values.keySet()) {
            String value = values.get(key)[0];
            if (key.startsWith("field")) {
                try {
                    int i = Integer.parseInt(key.replace("field", ""));
                    long l = Long.parseLong(value);
                    ids.put(i, l);
                } catch (NumberFormatException e) {
                    return ok(editor.render(null, null, null, null, fields, null, "Invalid field value"));
                }
            }
        }
        Cla newCla = new Cla();
        newCla.setName(name);
        newCla.setText(markdown);
        newCla.setAuthor(session().get("admin"));
        newCla.setRevision(1);
        newCla.setIsDefault(false);
        newCla.setCreated(new Date());
        List<Cla> clas = Cla.find.where().eq("name", name).orderBy("revision DESC").findList();
        if (!clas.isEmpty()) {
            Cla prevCla = clas.iterator().next();
            newCla.setRevision(prevCla.getRevision() + 1);
        }
        Ebean.beginTransaction();
        try {
            Ebean.save(newCla);
            for (Integer key : ids.keySet()) {
                Long id = ids.get(key);
                SqlUpdate sql = Ebean
                        .createSqlUpdate("INSERT INTO ClaInputFields (claId, inputFieldId) VALUES (:claId, :inputFieldId)");
                sql.setParameter("claId", newCla.getId());
                sql.setParameter("inputFieldId", id);
                sql.execute();
            }
            Ebean.commitTransaction();
        } finally {
            Ebean.endTransaction();
        }
        clas = Cla.find.where().eq("name", name).orderBy("revision DESC").findList();
        ObjectNode json = Json.newObject();
        for (Cla cla : clas) {
            json.put(Integer.toString(cla.getRevision()), Json.toJson(cla));
        }
        String message = "Revision " + newCla.getRevision() + " has been created";
        return ok(editor.render(clas, name, markdown, json.toString(), fields, message, null));
    }

    private static Result doDefaultCla(String successMessage, String errorMessage) {
        List<Cla> clas = Cla.find.orderBy("revision").orderBy("name").findList();
        return ok(defaultCla.render(clas, successMessage, errorMessage));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result defaultCla() {
        return doDefaultCla(null, null);
    }

    @Security.Authenticated(Authenticator.class)
    @Transactional
    public static Result updateDefault() {
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        long claId = -1;
        try {
            claId = Long.parseLong(ClaController.getField("cla", values, 0));
        } catch (IllegalStateException e) {
            return doDefaultCla(null, e.getMessage());
        } catch (NumberFormatException e) {
            return doDefaultCla(null, "Invalid CLA value");
        }
        Cla oldDefault = Cla.find.setForUpdate(true).where().eq("isDefault", true).findUnique();
        Cla newDefault = Cla.find.setForUpdate(true).where().eq("id", claId).findUnique();
        if (newDefault == null) {
            return doDefaultCla(null, "The CLA does not exist");
        }
        if (oldDefault == null) {
            newDefault.setIsDefault(true);
            Ebean.save(newDefault);
        } else if (oldDefault != null && oldDefault.getId().longValue() != claId) {
            oldDefault.setIsDefault(false);
            Ebean.save(oldDefault);
            newDefault.setIsDefault(true);
            Ebean.save(newDefault);
        }
        return doDefaultCla("The default CLA has been updated", null);
    }

    private static List<Cla> getFilteredClas() {
        List<Cla> clas = Cla.find.orderBy("revision DESC").findList();
        Map<String, Cla> filtered = new LinkedHashMap<String, Cla>();
        for (Cla cla : clas) {
            if (!filtered.containsKey(cla.getName())) {
                filtered.put(cla.getName(), cla);
            }
        }
        if (filtered.values() instanceof List) {
            clas = (List<Cla>) filtered.values();
        } else {
            clas = new ArrayList<Cla>(filtered.values());
        }
        return clas;
    }

    @Security.Authenticated(Authenticator.class)
    public static Result inputFields() {
        List<InputField> fields = InputField.find.findList();
        return ok(inputFields.render(fields));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result newInputField() {
        return ok(newInputField.render(null, null));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result addInputField() {
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        boolean employerChecked = values.containsKey("employerCheck");
        try {
            String name = ClaController.getField("name", values, 64);
            if (InputField.find.where().eq("displayName", name).findUnique() != null) {
                return ok(newInputField.render(null, "An input field with this display name already exists"));
            }
            InputField field = new InputField();
            field.setDisplayName(name);
            field.setRequiredForEmployer(employerChecked);
            Ebean.save(field);
            return ok(newInputField.render("The input field has been created", null));
        } catch (IllegalStateException e) {
            return ok(newInputField.render(null, e.getMessage()));
        }
    }

    @Security.Authenticated(Authenticator.class)
    public static Result manage() {
        List<Cla> clas = getFilteredClas();
        return ok(manage.render(clas, new PrettyTime()));
    }

    private static Map<String, String> parseLinkHeader(String header) {
        Map<String, String> pages = new HashMap<String, String>();
        String[] links = header.split(",");
        for (String link : links) {
            String[] segments = link.split(";");
            if (segments.length != 2) {
                continue;
            }
            String url = segments[0].trim().replace("<", "").replace(">", "");
            String rel = segments[1].trim().replace("rel=", "").replace("\"", "");
            pages.put(rel, url);
        }
        return pages;
    }

    private static List<String> getGitHubProjects() {
        List<String> projects = new ArrayList<String>();
        String header = GitHubApiUtils.getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        List<Organization> orgs = Organization.find.findList();
        for (Organization org : orgs) {
            String url = "https://api.github.com/orgs/%s/repos?per_page=100";
            url = String.format(url, org.getName());
            boolean hasNext = true;
            while (hasNext) {
                Promise<WSResponse> response = WS.url(url).setHeader("Authorization", header).get();
                WSResponse wsResponse = response.get(30000);
                JsonNode json = wsResponse.asJson();
                if (json.isArray()) {
                    ArrayNode array = (ArrayNode) json;
                    Iterator<JsonNode> iterator = array.iterator();
                    while (iterator.hasNext()) {
                        JsonNode node = iterator.next();
                        projects.add(org.getName() + "/" + node.get("name").asText());
                    }
                }
                hasNext = false;
                String link = wsResponse.getHeader("Link");
                if (link != null) {
                    Map<String, String> pages = parseLinkHeader(link);
                    if (pages.containsKey("next")) {
                        url = pages.get("next");
                        hasNext = true;
                    }
                }
            }
        }
        return projects;
    }

    @Security.Authenticated(Authenticator.class)
    public static Result manageProjects() {
        Map<String, ProjectCla> projects = new LinkedHashMap<String, ProjectCla>();
        List<String> gitHubProjects = getGitHubProjects();
        for (String project : gitHubProjects) {
            projects.put(project, null);
        }
        List<ProjectCla> projectClas = ProjectCla.find.findList();
        for (ProjectCla cla : projectClas) {
            projects.put(cla.getProject(), cla);
        }
        return ok(manageProjects.render(projects));
    }

    private static Result doManageProject(String project, String successMessage, String errorMessage) {
        List<Cla> clas = getFilteredClas();
        String claName = null;
        Integer claMaxRevision = null;
        Integer minRevision = null;
        Integer maxRevision = null;
        ProjectCla projectCla = ProjectCla.find.where().eq("project", project).findUnique();
        if (projectCla != null) {
            claName = projectCla.getMinCla().getName();
            for (Cla cla : clas) {
                if (cla.getName().equals(claName)) {
                    claMaxRevision = cla.getRevision();
                }
            }
            minRevision = projectCla.getMinCla().getRevision();
            maxRevision = projectCla.getMaxCla().getRevision();
        }
        return ok(
                mapCla.render(project, clas, claName, claMaxRevision, minRevision, maxRevision, successMessage, errorMessage));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result manageProject(String project) {
        return doManageProject(project, null, null);
    }

    public static void installWebhook(String org, String repo) {
        String projectPath = org + "/" + repo;
        InstalledWebhook webhook = InstalledWebhook.find.where().eq("project", projectPath).findUnique();
        if (webhook == null) {
            webhook = new InstalledWebhook();
            webhook.setProject(projectPath);
            Ebean.save(webhook);
        }
        String callbackUrl = Play.application().configuration().getString("app.host")
                + controllers.routes.ClaController.pullRequestHookCallback().url();
        GitHubApiUtils.registerHook(org, repo, "pull_request", callbackUrl);
    }

    @Security.Authenticated(Authenticator.class)
    @Transactional
    public static Result updateProjectMapping(String project) {
        String[] parts = project.split("/");
        if (parts.length != 2) {
            return doManageProject(project, null, "Project format must be org/repo");
        }
        ProjectCla projectCla = ProjectCla.find.setForUpdate(true).where().eq("project", project).findUnique();
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        String cla = null;
        try {
            cla = ClaController.getField("cla", values, 0);
        } catch (IllegalStateException e) {
            return doManageProject(project, null, e.getMessage());
        }
        if (cla.equals("Default")) {
            if (projectCla != null) {
                Ebean.delete(projectCla);
            }
            installWebhook(parts[0], parts[1]);
            return doManageProject(project, "The project mapping has been updated", null);
        }
        int minRevision = -1;
        int maxRevision = -1;
        try {
            minRevision = Integer.parseInt(ClaController.getField("minRevision", values, 0));
            maxRevision = Integer.parseInt(ClaController.getField("maxRevision", values, 0));
        } catch (IllegalStateException e) {
            return doManageProject(project, null, e.getMessage());
        } catch (NumberFormatException e) {
            return doManageProject(project, null, "Invalid revision value");
        }
        if (minRevision > maxRevision) {
            return doManageProject(project, null, "The minimum revision must be less than or equal to the maximum revision");
        }
        Cla minCla = Cla.find.where().eq("name", cla).eq("revision", minRevision).findUnique();
        Cla maxCla = Cla.find.where().eq("name", cla).eq("revision", maxRevision).findUnique();
        if (minCla == null || maxCla == null) {
            return doManageProject(project, null, "The CLA does not exist");
        }
        if (projectCla == null) {
            projectCla = new ProjectCla();
            projectCla.setProject(project);
        }
        projectCla.setMinCla(minCla);
        projectCla.setMaxCla(maxCla);
        Ebean.save(projectCla);
        installWebhook(parts[0], parts[1]);
        return doManageProject(project, "The project mapping has been updated", null);
    }

    @Security.Authenticated(Authenticator.class)
    public static Result search() {
        return ok(search.render());
    }

    @Security.Authenticated(Authenticator.class)
    public static Result ajaxProjectClas() {
        List<ProjectCla> clas = ProjectCla.find.findList();
        return ok(Json.toJson(clas));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result ajaxSearch(String project, String state) {
        ExpressionList<SignedCla> expression = SignedCla.find.where();
        if (StringUtils.isNotBlank(project)) {
            expression = expression.eq("project", project);
        }
        if (StringUtils.isNotBlank(state)) {
            expression = expression.eq("state", state);
        }
        List<SignedCla> clas = expression.findList();
        return ok(Json.toJson(clas));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result review(String uuid) {
        SignedCla cla = SignedCla.find.where().eq("uuid", uuid).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        String successMessage = flash("success");
        String errorMessage = flash("error");
        return ok(review.render(cla, new PrettyTime(), successMessage, errorMessage));
    }

    private static void logReviewEvent(SignedCla cla) {
        Review review = new Review();
        review.setSignedCla(cla);
        review.setReviewer(session().get("admin"));
        review.setState(cla.getState());
        review.setCreated(new Date());
        Ebean.save(review);
    }

    private static Result getNextSignedCla() {
        SignedCla cla = SignedCla.find.where().eq("state", SignedCla.STATE_PENDING).orderBy("lastUpdated").setMaxRows(1)
                .findUnique();
        if (cla != null) {
            return redirect(controllers.routes.AdminController.review(cla.getUuid()));
        } else {
            return redirect(controllers.routes.AdminController.search());
        }
    }

    private static void removeClaRejectedLabel(String labelUrl) {
        boolean hasLabel = false;
        String header = GitHubApiUtils.getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(labelUrl).setHeader("Authorization", header).get();
        WSResponse wsResponse = response.get(30000);
        JsonNode json = wsResponse.asJson();
        if (json.isArray()) {
            ArrayNode array = (ArrayNode) json;
            Iterator<JsonNode> iterator = array.iterator();
            while (iterator.hasNext()) {
                JsonNode node = iterator.next();
                if (ClaController.CLA_REJECTED_LABEL.equals(node.get("name").asText())) {
                    hasLabel = true;
                    break;
                }
            }
        }
        if (hasLabel) {
            labelUrl += "/" + ClaController.CLA_REJECTED_LABEL;
            response = WS.url(labelUrl).setHeader("Authorization", header).delete();
            wsResponse = response.get(30000);
            Logger.info("Label status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
        }
    }

    @Security.Authenticated(Authenticator.class)
    @Transactional
    public static Result approve(String uuid) {
        SignedCla cla = SignedCla.find.setForUpdate(true).where().eq("uuid", uuid).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        if (!cla.getState().equals(SignedCla.STATE_PENDING)) {
            flash("error", "The contributor license agreement has already been reviewed");
            return redirect(controllers.routes.AdminController.review(uuid));
        }
        cla.setState(SignedCla.STATE_APPROVED);
        Ebean.save(cla);
        logReviewEvent(cla);
        String status = Messages.get("github.status.approved");
        String comment = Messages.get("github.issue.approved", cla.getGitHubLogin());
        for (SignedClaGitHubPullRequest pullRequest : cla.getPullRequests()) {
            GitHubApiUtils.updateGitHubStatus(cla.getUuid(), "success", status, pullRequest.getGitHubStatusUrl());
            GitHubApiUtils.addIssueComment(comment, pullRequest.getGitHubIssueUrl() + "/comments");
            removeClaRejectedLabel(pullRequest.getGitHubIssueUrl() + "/labels");
        }
        flash("success", "The " + cla.getProject() + " project CLA for " + cla.getGitHubLogin() + " has been reviewed");
        return getNextSignedCla();
    }

    @Security.Authenticated(Authenticator.class)
    @Transactional
    public static Result reject(String uuid) {
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        String comment = null;
        try {
            comment = ClaController.getField("comment", values, 512);
        } catch (IllegalStateException e) {
            flash("error", e.getMessage());
            return redirect(controllers.routes.AdminController.review(uuid));
        }
        SignedCla cla = SignedCla.find.setForUpdate(true).where().eq("uuid", uuid).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        if (!cla.getState().equals(SignedCla.STATE_PENDING)) {
            flash("error", "The contributor license agreement has already been reviewed");
            return redirect(controllers.routes.AdminController.review(uuid));
        }
        cla.setState(SignedCla.STATE_REJECTED);
        cla.setUpdateComment(comment);
        Ebean.save(cla);
        logReviewEvent(cla);
        String status = Messages.get("github.status.rejected");
        String link = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                + controllers.routes.ClaController.index(cla.getUuid()).url());
        for (SignedClaGitHubPullRequest pullRequest : cla.getPullRequests()) {
            GitHubApiUtils.updateGitHubStatus(cla.getUuid(), "failure", status, pullRequest.getGitHubStatusUrl());
            String header = GitHubApiUtils.getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
            Promise<WSResponse> response = WS.url(pullRequest.getGitHubPullRequestUrl()).setHeader("Authorization", header)
                    .get();
            WSResponse wsResponse = response.get(30000);
            JsonNode json = wsResponse.asJson();
            if (json.get("merged").asBoolean()) {
                comment = Messages.get("github.issue.rejected.merged", cla.getGitHubLogin(), link);
            } else {
                comment = Messages.get("github.issue.rejected", cla.getGitHubLogin(), link);
            }
            GitHubApiUtils.addIssueComment(comment, pullRequest.getGitHubIssueUrl() + "/comments");
            GitHubApiUtils.attachIssueLabel(pullRequest.getGitHubIssueUrl() + "/labels", ClaController.CLA_REJECTED_LABEL);
        }
        flash("success", "The " + cla.getProject() + " project CLA for " + cla.getGitHubLogin() + " has been reviewed");
        return getNextSignedCla();
    }

    @Security.Authenticated(Authenticator.class)
    public static Result admins() {
        List<Admin> administrators = Admin.find.findList();
        String successMessage = flash("success");
        String errorMessage = flash("error");
        return ok(admins.render(administrators, successMessage, errorMessage));
    }

    @Security.Authenticated(Authenticator.class)
    public static Result addAdmin() {
        MultipartFormData data = request().body().asMultipartFormData();
        Map<String, String[]> values = data.asFormUrlEncoded();
        String user = null;
        try {
            user = ClaController.getField("admin", values, 64);
        } catch (IllegalArgumentException e) {
            flash("error", e.getMessage());
            return redirect(controllers.routes.AdminController.admins());
        }
        if (Admin.find.where().eq("admin", user).findUnique() != null) {
            flash("error", "The administrator " + user + " already exists");
            return redirect(controllers.routes.AdminController.admins());
        }
        Admin admin = new Admin();
        admin.setAdmin(user);
        admin.setIsSuper(false);
        Ebean.save(admin);
        flash("success", "Administrator " + user + " added");
        return redirect(controllers.routes.AdminController.admins());
    }

    @Security.Authenticated(Authenticator.class)
    public static Result removeAdmin(Long id) {
        Admin admin = Admin.find.where().eq("id", id).findUnique();
        if (admin != null && !admin.getIsSuper()) {
            Ebean.delete(admin);
            flash("success", "Administrator " + admin.getAdmin() + " removed");
        }
        return redirect(controllers.routes.AdminController.admins());
    }

    @Security.Authenticated(Authenticator.class)
    public static Result javascriptRoutes() {
        response().setContentType("text/javascript");
        return ok(Routes.javascriptRouter("jsRoutes", routes.javascript.AdminController.ajaxCheckClaName(),
                routes.javascript.AdminController.ajaxSearch(), routes.javascript.AdminController.review()));
    }

    @Transactional
    public static Result rejectExpiredCclas() {
        String cclaExpiration = Play.application().configuration().getString("app.ccla.expiration");
        Date date = new Date();
        Date expirationDate = DateUtils.addDays(date, Integer.parseInt(cclaExpiration) * -1);
        List<SignedCla> clas = SignedCla.find.setForUpdate(true).where().eq("state", SignedCla.STATE_PENDING_EXTERNAL)
                .lt("lastUpdated", expirationDate).findList();
        for (SignedCla cla : clas) {
            cla.setState(SignedCla.STATE_REJECTED);
            cla.setLegalState(SignedCla.STATE_REJECTED);
            Ebean.save(cla);
            String link = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                    + controllers.routes.ClaController.index(cla.getUuid()).url());
            String comment = Messages.get("github.issue.expired", cla.getGitHubLogin(), cclaExpiration, link);
            String status = Messages.get("github.status.expired");
            for (SignedClaGitHubPullRequest pullRequest : cla.getPullRequests()) {
                GitHubApiUtils.addIssueComment(comment, pullRequest.getGitHubIssueUrl() + "/comments");
                GitHubApiUtils.updateGitHubStatus(cla.getUuid(), "failure", status, pullRequest.getGitHubStatusUrl());
                GitHubApiUtils.attachIssueLabel(pullRequest.getGitHubIssueUrl() + "/labels", ClaController.CLA_REJECTED_LABEL);
            }
        }
        return ok();
    }
}
