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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.avaje.ebean.Ebean;
import com.fasterxml.jackson.databind.JsonNode;

import models.Cla;
import models.ProjectCla;
import models.SignedCla;
import play.Play;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;
import utils.GitHubApiUtils;
import views.html.claPreview;
import views.html.message;

public class ApiController extends Controller {
    public static Result clas() {
        Map<String, String> claMap = new LinkedHashMap<String, String>();
        String url = Play.application().configuration().getString("app.internal.host")
                + controllers.routes.ApiController.previewDefault().url();
        claMap.put("Default CLA", url);
        List<Cla> clas = Cla.find.orderBy("revision").orderBy("name").findList();
        for (Cla cla : clas) {
            String displayName = cla.getName() + " Revision " + cla.getRevision();
            url = Play.application().configuration().getString("app.internal.host")
                    + controllers.routes.ApiController.previewCla(cla.getName(), cla.getRevision());
            claMap.put(displayName, url);
        }
        return ok(Json.toJson(claMap));
    }

    public static Result previewDefault() {
        Cla cla = Cla.find.where().eq("isDefault", true).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        return ok(claPreview.render(cla));
    }

    public static Result previewCla(String name, Integer revision) {
        Cla cla = Cla.find.where().eq("name", name).eq("revision", revision).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        return ok(claPreview.render(cla));
    }

    public static Result setupCla(String name, Integer revision, String org, String repo) {
        Cla cla = null;
        if (name.equals("Default")) {
            cla = Cla.find.where().eq("isDefault", true).findUnique();
        } else {
            cla = Cla.find.where().eq("name", name).eq("revision", revision).findUnique();
        }
        if (cla == null) {
            return notFound("The contributor license agreement request does not exist");
        }
        ProjectCla projectCla = ProjectCla.find.setForUpdate(true).where().eq("project", org + "/" + repo).findUnique();
        if (name.equals("Default")) {
            if (projectCla != null) {
                Ebean.delete(projectCla);
            }
        } else {
            if (projectCla == null) {
                projectCla = new ProjectCla();
                projectCla.setProject(org + "/" + repo);
            }
            projectCla.setMinCla(cla);
            projectCla.setMaxCla(cla);
            Ebean.save(projectCla);
        }
        AdminController.installWebhook(org, repo);
        return ok();
    }

    public static Result importPullRequests(String org, String repo, String claName, Integer revision) {
        List<String> prs = GitHubApiUtils.getOpenPullRequests(org, repo);
        if (prs.isEmpty()) {
            return ok();
        }
        Cla cla = null;
        if (claName.equals("Default")) {
            cla = Cla.find.where().eq("isDefault", true).findUnique();
        } else {
            cla = Cla.find.where().eq("name", claName).eq("revision", revision).findUnique();
        }
        if (cla == null) {
            return notFound("The contributor license agreement request does not exist");
        }
        String header = GitHubApiUtils.getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        String url = "https://api.github.com/repos/%s/%s/pulls/%s";
        for (String pr : prs) {
            String apiUrl = String.format(url, org, repo, pr);
            Promise<WSResponse> response = WS.url(apiUrl).setHeader("Authorization", header).get();
            JsonNode json = response.get(30000).asJson();
            JsonNode userNode = json.get("user");
            JsonNode baseNode = json.get("base");
            JsonNode repoNode = baseNode.get("repo");
            String uid = userNode.get("id").asText();
            String login = userNode.get("login").asText();
            String pullRequestUrl = json.get("url").asText();
            String statusUrl = json.get("statuses_url").asText();
            String issueUrl = json.get("issue_url").asText();
            String repoUrl = repoNode.get("url").asText();
            /* Organization members do not need to sign a CLA */
            if (GitHubApiUtils.isOrgMember(org, login)) {
                GitHubApiUtils.addIssueLabel(repoUrl, ClaController.CLA_NOT_REQUIRED_LABEL, "159818");
                GitHubApiUtils.attachIssueLabel(issueUrl + "/labels", ClaController.CLA_NOT_REQUIRED_LABEL);
                continue;
            }
            SignedCla signedCla = SignedCla.find.setForUpdate(true).where().eq("claId", cla.getId()).eq("gitHubUid", uid)
                    .ne("state", SignedCla.STATE_REVOKED).findUnique();
            ProjectCla projectCla = new ProjectCla();
            projectCla.setProject(org + "/" + repo);
            projectCla.setMaxCla(cla);
            ClaController.handlePullRequest(signedCla, projectCla, uid, login, pullRequestUrl, statusUrl, issueUrl, repoUrl);
        }
        return ok();
    }
}
