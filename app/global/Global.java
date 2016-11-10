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

package global;

import static play.mvc.Results.internalServerError;
import static play.mvc.Results.notFound;

import java.util.ArrayList;
import java.util.List;

import it.innove.play.pdf.PdfGenerator;
import models.InstalledWebhook;
import models.ProjectDco;
import models.SignedClaGitHubPullRequest;
import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.Play;
import play.libs.F.Promise;
import play.libs.mailer.Email;
import play.libs.mailer.MailerPlugin;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import utils.GitHubApiUtils;
import views.html.message;

public class Global extends GlobalSettings {
    @Override
    public void onStart(Application application) {
        List<String> fonts = new ArrayList<String>();
        fonts.add("fonts/OpenSans-Regular.ttf");
        PdfGenerator.loadLocalFonts(fonts);

        /*
         * GitHub does not have an API to find undelivered webhook requests, so iterate through open pull requests to see if
         * there are any that we missed during downtime and send notification
         */
        List<String> missedPrs = new ArrayList<String>();
        List<InstalledWebhook> webhooks = InstalledWebhook.find.findList();
        List<ProjectDco> projectDcos = ProjectDco.find.findList();
        for (InstalledWebhook webhook : webhooks) {
            boolean skip = false;
            for (ProjectDco projectDco : projectDcos) {
                if (webhook.getProject().equals(projectDco.getProject())) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }

            String[] parts = webhook.getProject().split("/");
            if (parts.length != 2) {
                continue;
            }
            String org = parts[0];
            String repo = parts[1];
            String gitHubPullRequestUrlFormat = "https://api.github.com/repos/%s/%s/pulls/%s";
            List<String> prs = GitHubApiUtils.getOpenPullRequests(org, repo);
            for (String pr : prs) {
                String gitHubPullRequestUrl = String.format(gitHubPullRequestUrlFormat, org, repo, pr);
                if (SignedClaGitHubPullRequest.find.where().eq("gitHubPullRequestUrl", gitHubPullRequestUrl)
                        .findUnique() == null) {
                    missedPrs.add(webhook.getProject() + " - " + pr);
                }
            }
        }
        if (!missedPrs.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append(
                    "The following pull requests were not found in the database. Consider replaying the undelivered webhook requests to process them.\n\n");
            for (String missedPr : missedPrs) {
                builder.append(missedPr);
                builder.append("\n");
            }
            Email email = new Email();
            email.setSubject("[CLA Portal] Missing Pull Requests");
            email.setFrom(Play.application().configuration().getString("app.noreply.email"));
            email.addTo(Play.application().configuration().getString("app.admin.email"));
            email.setBodyText(builder.toString());
            MailerPlugin.send(email);
        }
    }

    @Override
    public Promise<Result> onHandlerNotFound(RequestHeader request) {
        return Promise.<Result> pure(notFound(message.render("The page you have requested does not exist")));
    }

    @Override
    public Promise<Result> onError(RequestHeader request, Throwable throwable) {
        Logger.error("Uncaught exception", throwable);
        return Promise
                .<Result> pure(internalServerError(message.render("Something went terribly wrong, please try again later")));
    }
}
