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

import org.ocpsoft.prettytime.PrettyTime;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.annotation.Transactional;

import models.SignedCla;
import models.SignedClaGitHubPullRequest;
import play.Play;
import play.i18n.Messages;
import play.libs.mailer.Email;
import play.libs.mailer.MailerPlugin;
import play.mvc.Controller;
import play.mvc.Result;
import utils.GitHubApiUtils;
import views.html.externalReview;
import views.html.message;
import views.html.reviewEmail;

public class ExternalReviewController extends Controller {
    public static Result review(String uuid, String token) {
        SignedCla cla = SignedCla.find.where().eq("uuid", uuid).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        String checkToken = ClaController.getToken(uuid, cla.getLegalContactEmail());
        if (!checkToken.equals(token)) {
            return forbidden(message.render("You do not have access to this page"));
        }
        session().put("user", cla.getLegalContactEmail());
        return ok(externalReview.render(cla, new PrettyTime()));
    }

    @Transactional
    public static Result approve(String uuid) {
        String email = session("user");
        if (email == null) {
            return forbidden(message.render("You must be signed in to access this page"));
        }
        SignedCla cla = SignedCla.find.setForUpdate(true).where().eq("uuid", uuid).eq("legalContactEmail", email).findUnique();
        if (cla == null) {
            session().clear();
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        if (!cla.getLegalState().equals(SignedCla.STATE_PENDING)) {
            session().clear();
            return badRequest(message.render("The contributor license agreement has already been reviewed"));
        }
        cla.setState(SignedCla.STATE_PENDING);
        cla.setLegalState(SignedCla.STATE_APPROVED);
        String comment = Messages.get("github.issue.approvedexternal", cla.getGitHubLogin());
        String status = Messages.get("github.status.approved");
        Ebean.save(cla);
        for (SignedClaGitHubPullRequest pullRequest : cla.getPullRequests()) {
            GitHubApiUtils.addIssueComment(comment, pullRequest.getGitHubIssueUrl() + "/comments");
            GitHubApiUtils.updateGitHubStatus(cla.getUuid(), "success", status, pullRequest.getGitHubStatusUrl());
        }

        /* E-mail to internal reviewers */
        String url = Play.application().configuration().getString("app.internal.host")
                + controllers.routes.AdminController.review(uuid).url();
        String body = reviewEmail.render(url, cla.getProject(), cla.getGitHubLogin(), cla.getEmail()).body();
        Email mail = new Email();
        mail.setSubject("New CLA Signed");
        mail.setFrom(Play.application().configuration().getString("app.noreply.email"));
        mail.addTo(Play.application().configuration().getString("app.notification.email"));
        mail.setBodyHtml(body);
        MailerPlugin.send(mail);
        session().clear();
        return ok(message.render("The <mark>" + cla.getProject() + "</mark> project CLA for <mark>" + cla.getGitHubLogin()
                + "</mark> has been reviewed. Please close this window."));
    }

    @Transactional
    public static Result reject(String uuid) {
        String email = session("user");
        if (email == null) {
            return forbidden(message.render("You must be signed in to access this page"));
        }
        SignedCla cla = SignedCla.find.setForUpdate(true).where().eq("uuid", uuid).eq("legalContactEmail", email).findUnique();
        if (cla == null) {
            session().clear();
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        if (!cla.getLegalState().equals(SignedCla.STATE_PENDING)) {
            session().clear();
            return badRequest(message.render("The contributor license agreement has already been reviewed"));
        }
        cla.setState(SignedCla.STATE_REJECTED);
        cla.setLegalState(SignedCla.STATE_REJECTED);
        Ebean.save(cla);
        String link = GitHubApiUtils.formatLink("here", Play.application().configuration().getString("app.host")
                + controllers.routes.ClaController.index(cla.getUuid()).url());
        String comment = Messages.get("github.issue.rejectedexternal", cla.getGitHubLogin(), link);
        String status = Messages.get("github.status.rejectedexternal");
        for (SignedClaGitHubPullRequest pullRequest : cla.getPullRequests()) {
            GitHubApiUtils.addIssueComment(comment, pullRequest.getGitHubIssueUrl() + "/comments");
            GitHubApiUtils.updateGitHubStatus(cla.getUuid(), "failure", status, pullRequest.getGitHubStatusUrl());
            GitHubApiUtils.attachIssueLabel(pullRequest.getGitHubIssueUrl() + "/labels", ClaController.CLA_REJECTED_LABEL);
        }
        session().clear();
        return ok(message.render("The <mark>" + cla.getProject() + "</mark> project CLA for <mark>" + cla.getGitHubLogin()
                + "</mark> has been reviewed. Please close this window."));
    }
}
