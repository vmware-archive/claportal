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

package models;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;

import play.db.ebean.Model;

@Entity
@Table(name = "SignedClas")
public class SignedCla extends Model {
    private static final long serialVersionUID = 1L;

    public static final String STATE_NEW = "New";
    public static final String STATE_PENDING_EXTERNAL = "Pending External Review";
    public static final String STATE_PENDING = "Pending Review";
    public static final String STATE_APPROVED = "Approved";
    public static final String STATE_REJECTED = "Rejected";
    public static final String STATE_REVOKED = "Revoked";

    public static final Map<String, String> REVOKE_REASONS = new HashMap<String, String>();

    static {
        REVOKE_REASONS.put("i2c",
                "I was previously contributing as an individual, and will now contribute on behalf of a company");
        REVOKE_REASONS.put("c2i",
                "I was previously contributing on behalf of a company, and will now contribute as an individual");
        REVOKE_REASONS.put("c2c", "I was previously contributing on behalf of a company, and now work for a different company");
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String uuid;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "claId")
    private Cla cla;
    private String project;
    @Column(name = "gitHubUid")
    private String gitHubUid;
    @Column(name = "gitHubLogin")
    private String gitHubLogin;
    @OneToMany(mappedBy = "signedCla", cascade = { CascadeType.ALL })
    private List<SignedClaGitHubPullRequest> pullRequests;
    private String state;
    private Date created;
    @Version
    @Column(name = "lastUpdated")
    private Date lastUpdated;
    private String email;
    @Column(name = "legalContactEmail")
    private String legalContactEmail;
    @Column(name = "legalState")
    private String legalState;
    private String signature;
    @Column(name = "updateComment")
    private String updateComment;
    @Column(name = "revokeReason")
    private String revokeReason;
    @OneToMany(mappedBy = "signedCla", cascade = { CascadeType.ALL })
    private List<SignedClaInputField> inputFields;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Cla getCla() {
        return cla;
    }

    public void setCla(Cla cla) {
        this.cla = cla;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getGitHubUid() {
        return gitHubUid;
    }

    public void setGitHubUid(String gitHubUid) {
        this.gitHubUid = gitHubUid;
    }

    public String getGitHubLogin() {
        return gitHubLogin;
    }

    public void setGitHubLogin(String gitHubLogin) {
        this.gitHubLogin = gitHubLogin;
    }

    public List<SignedClaGitHubPullRequest> getPullRequests() {
        return pullRequests;
    }

    public void setPullRequests(List<SignedClaGitHubPullRequest> pullRequests) {
        this.pullRequests = pullRequests;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLegalContactEmail() {
        return legalContactEmail;
    }

    public void setLegalContactEmail(String legalContactEmail) {
        this.legalContactEmail = legalContactEmail;
    }

    public String getLegalState() {
        return legalState;
    }

    public void setLegalState(String legalState) {
        this.legalState = legalState;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getUpdateComment() {
        return updateComment;
    }

    public void setUpdateComment(String updateComment) {
        this.updateComment = updateComment;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public List<SignedClaInputField> getInputFields() {
        return inputFields;
    }

    public void setInputFields(List<SignedClaInputField> inputFields) {
        this.inputFields = inputFields;
    }

    public static Finder<Long, SignedCla> find = new Finder<Long, SignedCla>(Long.class, SignedCla.class);
}
