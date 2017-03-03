# --- !Ups

CREATE TABLE `Clas` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `name` VARCHAR(64) NOT NULL,
   `text` TEXT NOT NULL,
   `author` VARCHAR(64) NOT NULL,
   `revision` INT UNSIGNED NOT NULL,
   `isDefault` TINYINT(1) NOT NULL,
   `created` DATETIME NOT NULL,
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `InputFields` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `displayName` VARCHAR(64) NOT NULL,
   `requiredForEmployer` TINYINT(1) NOT NULL,
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `ClaInputFields` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `claId` INT UNSIGNED NOT NULL,
   `inputFieldId` INT UNSIGNED NOT NULL,
   PRIMARY KEY (`id`),
   FOREIGN KEY (`claId`) REFERENCES `Clas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
   FOREIGN KEY (`inputFieldId`) REFERENCES `InputFields` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `ProjectClas` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `minClaId` INT UNSIGNED NOT NULL,
   `maxClaId` INT UNSIGNED NOT NULL,
   `project` VARCHAR(512) NOT NULL,
   PRIMARY KEY (`id`),
   FOREIGN KEY (`minClaId`) REFERENCES `Clas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
   FOREIGN KEY (`maxClaId`) REFERENCES `Clas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `SignedClas` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `uuid` VARCHAR(32) NOT NULL,
   `claId` INT UNSIGNED NOT NULL,
   `project` VARCHAR(512) NOT NULL,
   `gitHubUid` VARCHAR(32) NOT NULL,
   `gitHubLogin` VARCHAR(128) NOT NULL,
   `state` VARCHAR(32) NOT NULL,
   `created` DATETIME NOT NULL,
   `lastUpdated` DATETIME NOT NULL,
   `email` VARCHAR(128) DEFAULT NULL,
   `legalContactEmail` VARCHAR(128) DEFAULT NULL,
   `legalState` VARCHAR(32) DEFAULT NULL,
   `signature` TEXT DEFAULT NULL,
   `updateComment` VARCHAR(512) DEFAULT NULL,
   `revokeReason` VARCHAR(256) DEFAULT NULL,
   PRIMARY KEY (`id`),
   FOREIGN KEY (`claId`) REFERENCES `Clas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `SignedClaInputFields` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `signedClaId` INT UNSIGNED NOT NULL,
   `inputFieldId` INT UNSIGNED NOT NULL,
   `response` VARCHAR(128) NOT NULL,
   PRIMARY KEY (`id`),
   FOREIGN KEY (`signedClaId`) REFERENCES `SignedClas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
   FOREIGN KEY (`inputFieldId`) REFERENCES `InputFields` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `SignedClaGitHubPullRequests` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `signedClaId` INT UNSIGNED NOT NULL,
   `gitHubPullRequestUrl` VARCHAR(256) NOT NULL,
   `gitHubStatusUrl` VARCHAR(256) NOT NULL,
   `gitHubIssueUrl` VARCHAR(256) NOT NULL,
   PRIMARY KEY (`id`),
   FOREIGN KEY (`signedClaId`) REFERENCES `SignedClas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `Dcos` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `text` TEXT NOT NULL,
   `author` VARCHAR(64) NOT NULL,
   `revision` INT UNSIGNED NOT NULL,
   `created` DATETIME NOT NULL,
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `ProjectDcos` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `project` VARCHAR(512) NOT NULL,
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `Admins` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin` VARCHAR(64) NOT NULL,
  `super` TINYINT(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `InstalledWebhooks` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `project` VARCHAR(512) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `Organizations` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `name` VARCHAR(64) NOT NULL,
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

CREATE TABLE `Reviews` (
   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
   `signedClaId` INT UNSIGNED NOT NULL,
   `reviewer` VARCHAR(64) NOT NULL,
   `state` VARCHAR(16) NOT NULL,
   `created` DATETIME NOT NULL,
   PRIMARY KEY (`id`),
   FOREIGN KEY (`signedClaId`) REFERENCES `SignedClas` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=UTF8;

ALTER TABLE `Admins` ADD INDEX `ix_Admins_admin` (`admin`);
ALTER TABLE `Clas` ADD INDEX `ix_Clas_name` (`name`);
ALTER TABLE `Clas` ADD INDEX `ix_Clas_revision` (`revision`);
ALTER TABLE `Clas` ADD INDEX `ix_Clas_isDefault` (`isDefault`);
ALTER TABLE `InputFields` ADD INDEX `ix_InputFields_displayName` (`displayName`);
ALTER TABLE `InstalledWebhooks` ADD INDEX `ix_InstalledWebhooks_project` (`project`);
ALTER TABLE `ProjectClas` ADD INDEX `ix_ProjectClas_project` (`project`);
ALTER TABLE `ProjectDcos` ADD INDEX `ix_ProjectDcos_project` (`project`);
ALTER TABLE `SignedClaGitHubPullRequests` ADD INDEX `ix_SignedClaGitHubPullRequests_gitHubPullRequestUrl` (`gitHubPullRequestUrl`);
ALTER TABLE `SignedClas` ADD INDEX `ix_SignedClas_uuid` (`uuid`);
ALTER TABLE `SignedClas` ADD INDEX `ix_SignedClas_state` (`state`);
ALTER TABLE `SignedClas` ADD INDEX `ix_SignedClas_gitHubUid` (`gitHubUid`);

INSERT INTO Admins (admin, super) VALUES ('claadmin', 1);
INSERT INTO Clas (name, text, author, revision, isDefault, created) VALUES ('Sample', 'Sample CLA', 'claadmin', 1, 1, '2016-01-01 00:00:00');
INSERT INTO InputFields (displayName, requiredForEmployer) VALUES ('Full legal name', 0);
INSERT INTO InputFields (displayName, requiredForEmployer) VALUES ('Address', 0);
INSERT INTO InputFields (displayName, requiredForEmployer) VALUES ('Job title', 1);
INSERT INTO InputFields (displayName, requiredForEmployer) VALUES ('Company name', 1);
INSERT INTO ClaInputFields (claId, inputFieldId) VALUES (1, 1);
INSERT INTO ClaInputFields (claId, inputFieldId) VALUES (1, 2);
INSERT INTO ClaInputFields (claId, inputFieldId) VALUES (1, 3);
INSERT INTO ClaInputFields (claId, inputFieldId) VALUES (1, 4);
INSERT INTO Organizations (name) VALUES ('testorg');

# --- !Downs
 
DROP TABLE Reviews;
DROP TABLE Organizations;
DROP TABLE InstalledWebhooks;
DROP TABLE Admins;
DROP TABLE ProjectDcos;
DROP TABLE Dcos;
DROP TABLE SignedClaGitHubPullRequests;
DROP TABLE SignedClaInputFields;
DROP TABLE SignedClas;
DROP TABLE ProjectClas;
DROP TABLE ClaInputFields;
DROP TABLE InputFields;
DROP TABLE Clas;
