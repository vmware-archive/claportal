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

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;

import play.db.ebean.Model;

@Entity
@Table(name = "SignedClaInputFields")
public class SignedClaInputField extends Model {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "signedClaId")
    @JsonBackReference
    private SignedCla signedCla;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inputFieldId")
    private InputField inputField;
    private String response;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SignedCla getSignedCla() {
        return signedCla;
    }

    public void setSignedCla(SignedCla signedCla) {
        this.signedCla = signedCla;
    }

    public InputField getInputField() {
        return inputField;
    }

    public void setInputField(InputField inputField) {
        this.inputField = inputField;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public static Finder<Long, SignedClaInputField> find = new Finder<Long, SignedClaInputField>(Long.class,
            SignedClaInputField.class);
}
