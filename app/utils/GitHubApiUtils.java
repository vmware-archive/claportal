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

package utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.Logger;
import play.Play;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

public class GitHubApiUtils {
    public static String getAuthHeader(String token) {
        String credentials = token + ":x-oauth-basic";
        String value = "Basic " + Base64.encodeBase64String(credentials.getBytes());
        return value;
    }

    public static void updateGitHubStatus(String uuid, String state, String description, String statusUrl) {
        ObjectNode statusNode = Json.newObject();
        statusNode = statusNode.put("context", "vmwclabot");
        statusNode = statusNode.put("state", state);
        statusNode = statusNode.put("description", description);
        String targetUrl = Play.application().configuration().getString("app.host")
                + controllers.routes.ClaController.index(uuid).url();
        statusNode = statusNode.put("target_url", targetUrl);
        String header = getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(statusUrl).setHeader("Authorization", header).post(statusNode);
        WSResponse wsResponse = response.get(30000);
        Logger.info("Update pull request status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
    }

    public static void addIssueComment(String comment, String commentUrl) {
        ObjectNode statusNode = Json.newObject();
        statusNode = statusNode.put("body", comment);
        String header = getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(commentUrl).setHeader("Authorization", header).post(statusNode);
        WSResponse wsResponse = response.get(30000);
        Logger.info("Comment status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
    }

    public static boolean isOrgMember(String org, String login) {
        String url = "https://api.github.com/orgs/%s/memberships/%s";
        url = String.format(url, org, login);
        String header = getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(url).setHeader("Authorization", header).get();
        WSResponse wsResponse = response.get(30000);
        Logger.info("Is org member status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
        return wsResponse.getStatus() == 200;
    }

    public static void addIssueLabel(String repoUrl, String label, String color) {
        String labelsUrl = repoUrl + "/labels";
        String json = "{ \"name\": \"" + label + "\", \"color\": \"" + color + "\" }";
        String header = GitHubApiUtils.getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(labelsUrl).setHeader("Authorization", header).post(json);
        WSResponse wsResponse = response.get(30000);
        Logger.info("Label status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
    }

    public static void attachIssueLabel(String labelUrl, String label) {
        String json = "[ \"" + label + "\" ]";
        String header = getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(labelUrl).setHeader("Authorization", header).post(json);
        WSResponse wsResponse = response.get(30000);
        Logger.info("Label status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
    }

    public static String formatLink(String name, String url) {
        return String.format("[%s](%s)", name, url);
    }

    public static void registerHook(String org, String repo, String event, String callbackUrl) {
        String url = "https://api.github.com/repos/%s/%s/hooks";
        url = String.format(url, org, repo);
        ObjectNode hookNode = Json.newObject();
        hookNode = hookNode.put("name", "web");
        hookNode = hookNode.put("active", true);
        ArrayNode eventsNode = Json.newObject().arrayNode();
        eventsNode.add(event);
        hookNode.put("events", eventsNode);
        ObjectNode configNode = Json.newObject();
        configNode = configNode.put("url", callbackUrl);
        configNode = configNode.put("content_type", "json");
        configNode = configNode.put("secret", "");
        hookNode.put("config", configNode);
        String header = getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(url).setHeader("Authorization", header).post(hookNode);
        WSResponse wsResponse = response.get(30000);
        Logger.info("Register hook status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
    }

    public static List<String> getOpenPullRequests(String org, String repo) {
        List<String> prs = new ArrayList<String>();
        String url = "https://api.github.com/repos/%s/%s/pulls";
        url = String.format(url, org, repo);
        String header = getAuthHeader(Play.application().configuration().getString("app.github.oauthtoken"));
        Promise<WSResponse> response = WS.url(url).setHeader("Authorization", header).get();
        WSResponse wsResponse = response.get(30000);
        Logger.info("Get open pull requests status [" + wsResponse.getStatus() + "]: " + wsResponse.getBody());
        JsonNode json = wsResponse.asJson();
        if (json.isArray()) {
            ArrayNode array = (ArrayNode) json;
            Iterator<JsonNode> iterator = array.iterator();
            while (iterator.hasNext()) {
                JsonNode node = iterator.next();
                int number = node.get("number").asInt();
                prs.add(Integer.toString(number));
            }
        }
        return prs;
    }
}
