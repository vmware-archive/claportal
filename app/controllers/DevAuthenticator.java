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

import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Security;

public class DevAuthenticator extends Security.Authenticator {
    @Override
    public String getUsername(Context context) {
        return context.session().get("uid");
    }

    @Override
    public Result onUnauthorized(Context context) {
        return redirect(controllers.routes.DevController.login());
    }
}
