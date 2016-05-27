# CLA Portal

This tool enables a workflow for developers to digitally sign a contributor license agreement (CLA) for your projects on GitHub. When a developer opens a pull request, they will get prompted to sign the agreement if they have not already. An administrator interface is provided for CLA authoring, CLA-to-project mapping, and agreement reviews.

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

5. Build it

    `activator docker:publishLocal`

6. Run it

    `docker run -p 9000:9000 claportal:1.0-SNAPSHOT`

7. Log in to localhost:9000/admin/login using credentials claadmin:claadmin

## Contributing

The CLA Portal project team welcomes contributions from the community. If you wish to contribute code and you have not signed our contributor license agreement (CLA), our bot will update the issue when you open a Pull Request. For any questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq).

## License

The CLA Portal is available under the [Apache 2 license](LICENSE).
