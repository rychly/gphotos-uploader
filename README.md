# GPhotosUploader

Website: https://rychly.gitlab.io/gphotos-uploader/ (for builds, usage-help, license, etc.)

Uploads missing media files from given directories into Google Photos and control their sharing.

## Dependencies

This project is a Java application and it utilizes
[Java client library](https://github.com/google/java-photoslibrary)
for the
[Google Photos API](https://developers.google.com/photos/library/guides/get-started-java).

## Google Photos Library API Credentials

### New Project/Application in Google Cloud Platform and its Credentials

It is necessary to create and to use just one application/project in Google Cloud Platform, i.e.,
to use credentials from the one application/project only (created as below).
Otherwise, users would not be able to share albums between multiple applications/projects
due to error "No permission to join this album since it was not created by this app".

1.	[Create a project in the Google Cloud Platform](https://console.developers.google.com/projectcreate):
	*	Project Name: `GPhotosUploader`
	*	Project ID: `gphotosuploader-0`
2.	[Select the created project](https://console.developers.google.com/apis/dashboard?project=gphotosuploader-0)
3.	[Enable Photos Library API in the API Library](https://console.developers.google.com/apis/library/photoslibrary.googleapis.com?project=gphotosuploader-0)
4.	[Add credentials to your project](https://console.developers.google.com/apis/credentials/wizard?project=gphotosuploader-0)
	*	API are you using: `Photos Library API`
	*	You will be calling the API from: `Other UI`
	*	You will be accessing: `User data`
	*	**Find out what kind of credentials you need!**
	*	OAuth 2.0 client name: `gphotosuploader-0`
	*	**Create OAuth client ID!**
	*	Product name shown to users: `GPhotosUploader`
	*	**Continue!**
5.	[Download credentials of OAuth 2.0 client ID in JSON](https://console.developers.google.com/apis/credentials?project=gphotosuploader-0)

### Using the Client Application with the Credentials

1.	Save the JSON credentials file into `${XDG_CONFIG_HOME}/GPhotosUploader/client_secret.json`
2.	Edit `${XDG_CONFIG_HOME}/GPhotosUploader/GPhotosUploader.properties` to add the credentials to a credentials profile (in the example, the credentials profile name is "username").
	The `client_secret.json` file (downloaded as above) and the `username-credentials` directory (will be created automaticaly if missing)
	should be in `${XDG_CONFIG_HOME}/GPhotosUploader` dirtectory. One identical `client_secret.json` file can be (and usually is) shared
	by multiple credential profiles.
~~~properties
google.api.credentials.client-secret.file.profile-username=client_secret.json
google.api.credentials.directory.profile-username=username-credentials
~~~
3.	Run the client application with the credentials profile "username" as `./run.sh -g username -l` and [authorize the application to access data of a particular Google account](https://accounts.google.com/o/oauth2/auth)
