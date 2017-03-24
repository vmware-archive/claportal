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
import java.util.List;
import java.util.Map;

import org.ocpsoft.prettytime.PrettyTime;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;

import it.innove.play.pdf.PdfGenerator;
import models.Cla;
import models.Dco;
import models.ProjectCla;
import models.ProjectDco;
import models.SignedCla;
import play.Play;
import play.libs.F.Promise;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import play.mvc.Security;
import utils.GitHubApiUtils;
import views.html.claPdf;
import views.html.claPreview;
import views.html.dcoPreview;
import views.html.devCla;
import views.html.devClas;
import views.html.devLogin;
import views.html.message;

public class DevController extends Controller {
    public static Result login() {
        String redirectUrl = Play.application().configuration().getString("app.host");
        redirectUrl += controllers.routes.DevController.authCallback(null).url();
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
        String authUrl = builder.toString();
        return ok(devLogin.render(authUrl));
    }

    public static Result authCallback(String code) {
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
        session().clear();
        session("uid", uid);
        return redirect(controllers.routes.DevController.viewClas());
    }

    @Security.Authenticated(DevAuthenticator.class)
    public static Result viewClas() {
        List<SignedCla> clas = SignedCla.find.where().eq("gitHubUid", session("uid")).findList();
        String successMessage = flash("success");
        String errorMessage = flash("error");
        return ok(devClas.render(clas, new PrettyTime(), successMessage, errorMessage));
    }

    @Security.Authenticated(DevAuthenticator.class)
    public static Result viewCla(String uuid) {
        SignedCla cla = SignedCla.find.where().eq("uuid", uuid).eq("gitHubUid", session("uid")).findUnique();
        if (cla == null) {
            flash("error", "The contributor license agreement does not exist");
            return redirect(controllers.routes.DevController.viewClas());
        }
        return ok(devCla.render(cla, new PrettyTime()));
    }

    @Security.Authenticated(DevAuthenticator.class)
    public static Result viewPdf(String uuid) {
        SignedCla cla = SignedCla.find.where().eq("uuid", uuid).eq("gitHubUid", session("uid")).findUnique();
        if (cla == null) {
            return notFound(message.render("The contributor license agreement does not exist"));
        }
        return PdfGenerator.ok(claPdf.render(cla), "");
    }

    @Security.Authenticated(DevAuthenticator.class)
    @Transactional
    public static Result revokeCla(String uuid) {
        SignedCla cla = SignedCla.find.setForUpdate(true).where().eq("uuid", uuid).eq("gitHubUid", session("uid"))
                .eq("state", SignedCla.STATE_APPROVED).findUnique();
        if (cla != null) {
            MultipartFormData data = request().body().asMultipartFormData();
            Map<String, String[]> values = data.asFormUrlEncoded();
            String reasonCode = null;
            try {
                reasonCode = ClaController.getField("reason", values, 0);
            } catch (IllegalArgumentException e) {
                flash("error", "You must provide a reason when revoking a contributor license agreement");
            }
            String reason = SignedCla.REVOKE_REASONS.get(reasonCode);
            if (reason != null) {
                cla.setState(SignedCla.STATE_REVOKED);
                cla.setRevokeReason(reason);
                Ebean.save(cla);
                flash("success", "The contributor license agreement has been revoked");
            } else {
                flash("error", "The reason code does not exist");
            }
        } else {
            flash("error", "The contributor license agreement does not exist");
        }
        return redirect(controllers.routes.DevController.viewClas());
    }

    public static Result previewRedirect(String org, String repo) {
        if (ProjectDco.find.where().eq("project", org + "/" + repo).findUnique() != null) {
            Dco dco = Dco.find.orderBy("revision DESC").setMaxRows(1).findUnique();
            if (dco == null) {
                return notFound(message.render("The developer certificate of origin request does not exist"));
            }
            return ok(dcoPreview.render(dco));
        }
        ProjectCla cla = ProjectCla.find.where().eq("project", org + "/" + repo).findUnique();
        if (cla != null) {
            Cla maxCla = cla.getMaxCla();
            return ok(claPreview.render(maxCla));
        }
        Cla defaultCla = Cla.find.where().eq("isDefault", true).findUnique();
        if (defaultCla == null) {
            return notFound(message.render("The contributor license agreement request does not exist"));
        }
        return ok(claPreview.render(defaultCla));
    }

    public static Result logout() {
        session().clear();
        return redirect(controllers.routes.DevController.login());
    }
}
