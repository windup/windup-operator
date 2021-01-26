import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.AbstractPromptReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList;



import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@GrabConfig(systemClassLoader = true)
@Grab('com.google.oauth-client:google-oauth-client:1.23.0')
@Grab('com.google.api-client:google-api-client:1.23.0')
@Grab('com.google.oauth-client:google-oauth-client-jetty:1.23.0')
@Grab('com.google.apis:google-api-services-drive:v3-rev110-1.23.0')
@Grab('com.google.apis:google-api-services-oauth2:v2-rev139-1.23.0')

class GoogleDriveFileDownloader {

    // User id used to access Google services
    private static String USER_ID = "windupdocs@gmail.com"
    
    private static final String APPLICATION_NAME = "perftest";
    private static final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved credentials/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static File CREDENTIALS_STORE_DIR = new File("/opt/perftest/.credentials/")
    private static File CLIENT_SECRETS_FILE = new File(CREDENTIALS_STORE_DIR, "client_secrets.json")
    private static final String TOKENS_DIRECTORY_PATH = "release-tokens";


    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport transport) throws IOException {
        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new java.io.File(CREDENTIALS_STORE_DIR, TOKENS_DIRECTORY_PATH));
        Reader clientSecretsIS = new FileReader(CLIENT_SECRETS_FILE);
        try {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, clientSecretsIS);

            // redirect to an authorization page
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientSecrets, SCOPES)
                    .setDataStoreFactory(dataStoreFactory)
                    .setAccessType("offline")
                    .build();
            VerificationCodeReceiver codeReceiver = new AbstractPromptReceiver() {
                @Override
                String getRedirectUri() throws Exception {
                    return "http://localhost";
                }

                @Override
                String waitForCode() {
                    String url = flow.newAuthorizationUrl().setRedirectUri(getRedirectUri()).build();
                    System.out.println("Browse here and get the token:")
                    System.out.println(url);
                    return super.waitForCode()
                }
            };


            AuthorizationCodeInstalledApp installedApp = new AuthorizationCodeInstalledApp(flow, codeReceiver);
            return installedApp.authorize(USER_ID);
        } finally {
            clientSecretsIS.close();
        }
    }

    static void main(String... args) throws IOException, GeneralSecurityException {
        if (args.length < 1)
        {
            println("ERROR: file ID to download missing, please provide one as first argument of this command.");
            System.exit(1);
        }
        String fileId = args[0];
        
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final Credential credential = getCredentials(HTTP_TRANSPORT)
        Drive driveService = new Drive.Builder(HTTP_TRANSPORT, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        String fileName = "";
        if (args.length == 2)
        {
            fileName = args[1];
        } else
        {
            fileName = driveService.files().get(fileId).execute().getName();
        }

        OutputStream outputStream = new FileOutputStream(fileName);
        driveService.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream);
    }
}
