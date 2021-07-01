# VMware has ended active development of this project, this repository will no longer be updated.
# CLA Portal

This tool enables a workflow for developers to digitally sign a contributor license agreement (CLA) for your projects on GitHub. When a developer opens a pull request, they will get prompted to sign the agreement if they have not already. An administrator interface is provided for CLA authoring, CLA-to-project mapping, and agreement reviews.

The portal also supports developer certificate of origin (DCO). When enabled on a repository, all commits within a pull request will be checked for a signature in the following format: `Signed-off-by: John Doe <john.doe@example.com>` that matches the commit author.

For more general information on CLAs, refer to our [FAQ](https://cla.vmware.com/faq)

## Try it out

### Prerequisites

* Java 8
* Lightbend Activator
* Docker
* ngrok

### Build & Run

1. Start ngrok to tunnel webhook requests from GitHub

    `ngrok http 9000`

2. Register an OAuth application on GitHub
3. Create an OAuth access token on GitHub with the following scope

    `repo admin:repo_hook read:org`

4. Update settings in conf/application.conf

    ```
    smtp.host="smtp.test.com"

    app.host="https://<subdomain>.ngrok.io"
    app.internal.host="http://localhost:9000"
    app.github.clientid="<GitHub OAuth client ID>"
    app.github.clientsecret="<GitHub OAuth client secret>"
    app.github.oauthtoken="<GitHub OAuth access token>"
    app.notification.email="claadmin@test.com"
    app.noreply.email="noreply@test.com"
    app.admin.email="claadmin@test.com"
    app.ccla.expiration=7
    ```

5. Update organization in conf/evolutions/default/1.sql

    `INSERT INTO Organizations (name) VALUES ('testorg');`

6. Generate a new application secret

    `activator playUpdateSecret`

7. Build it

    `activator docker:publishLocal`

8. Run it

    `docker run -p 9000:9000 claportal:1.0-SNAPSHOT`

9. Log in to localhost:9000/admin/login using credentials claadmin:claadmin (Note: These credentials are hard-coded in Authenticator.java. If you decide to use this in production, you should update the authenticate method for your identity provider.)

10. Select "Manage Projects" to load all repositories from your organization

11. Select a repository, click Edit, and set the CLA to Sample (Note: When you save, this will install the web hook to the repository on GitHub for pull request events.)

12. Submit a pull request to your repository (Note: Organization members do not sign the CLA, so submit a pull request using an account that is not a member of the organization.)

### Developer Certificate of Origin (DCO)

1. Log in to localhost:9000/admin/login using credentials claadmin:claadmin (Note: These credentials are hard-coded in Authenticator.java. If you decide to use this in production, you should update the authenticate method for your identity provider.)

2. Select "Edit DCO" to create the initial DCO agreement revision

3. Select "Manage Projects" to load all repositories from your organization

4. Select a repository, click Edit, and check "Use DCO" (Note: When you save, this will install the web hook to the repository on GitHub for pull request events.)

When enabled on a repository, all commits within a pull request will be checked for a signature in the following format: `Signed-off-by: John Doe <john.doe@example.com>` that matches the commit author.

## Contributing

The CLA Portal project team welcomes contributions from the community. If you wish to contribute code and you have not signed our contributor license agreement (CLA), our bot will update the issue when you open a Pull Request. For any questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq).

## License

The CLA Portal is available under the [Apache 2 license](LICENSE).
