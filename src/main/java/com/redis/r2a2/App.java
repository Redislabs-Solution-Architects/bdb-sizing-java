package com.redis.r2a2;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Scanner;
import java.util.ArrayList;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Hello world!
 */
public final class App {

    private String authToken;
    private JsonArray clusterConfig;
    private ArrayList<String> clusterPwd = new ArrayList();

    private String bdbURI = "/v1/bdbs?fields=uid,name,backup,data_persistence,eviction_policy,memory_size,module_list,replication,sharding,shards_count,version,bigstore,crdt,crdt_guid";

    private App() {

    }

    public void setBasicAuthString(String user, String password) {
        String secretKey = user + ":" + password;
        this.authToken = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public HttpsURLConnection executeGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setDoOutput(false);
        conn.setDoInput(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Basic " + this.authToken);
        conn.connect();

        System.out.println("[executeGet] " + conn.getResponseMessage());

        return conn;
    }

    public JsonArray getJsonArray(String urlStr) throws Exception {

        HttpsURLConnection conn = executeGet(urlStr);
        JsonReader rdr = Json.createReader(conn.getInputStream());
        JsonArray rec = rdr.readArray();
        conn.disconnect();
        return rec;

    }

    public void  loadConfigFile(String configFile) throws Exception {
        JsonReader rdr = Json.createReader(new FileInputStream(configFile));
        JsonArray jarr = rdr.readArray();

        Scanner s = new Scanner(System.in);

        for(int  i = 0; i < jarr.size(); i++) {
            JsonObject jobj = jarr.getJsonObject(i);
            System.out.print("Enter the Admin Password for " + jobj.getString("cluster_node") + ":");
            clusterPwd.add(s.nextLine());    
        }

        System.out.println("[loadConfigFile] Cluster Config Loaded");

        this.clusterConfig = jarr;
    }

    public void saveClusterDBDetails() throws Exception {
        PrintWriter pw = new PrintWriter("./bdb-report.csv");
        pw.println("cluster_name,db_name,version,usage_category,memory_size,search,data_persistence,replication,sharding,shard_count,backup,eviction_policy,flash,crdt,crdt_guid");

        //make api call for each cluster
        for(int c = 0; c < clusterConfig.size(); c++) {
            JsonObject clusterObj = clusterConfig.getJsonObject(c);

        
            setBasicAuthString(clusterObj.getString("cluster_admin"), clusterPwd.get(c));

            //make the api call
            String apiPort = "9443";

            try { apiPort = clusterObj.getString("cluster_api_port");}catch(Exception e){}

            JsonArray bdbArr = getJsonArray("https://" + clusterObj.getString("cluster_node") + ":" + apiPort + bdbURI);
            System.out.println("Number of DBs in Cluster: " + bdbArr.size() + " ");

            //loop though each DB in the cluster
            for(int b = 0; b < bdbArr.size(); b++) {
                JsonObject bdb = bdbArr.getJsonObject(b);
                String record = clusterObj.getString("cluster_node");
                String usageCategory = "Cache";
                String search = "FALSE";

                //find the usage category
                if(!"disabled".equalsIgnoreCase(bdb.getString("data_persistence")) || bdb.getBoolean("backup") || "noeviction".equalsIgnoreCase(bdb.getString("eviction_policy"))) {
                    usageCategory = "Database";
                }

                //if search enabled then database
                JsonArray moduleList = bdb.getJsonArray("module_list");

                try {
                    for(int m = 0; m < moduleList.size(); m++) {
                        JsonObject modules = moduleList.getJsonObject(m);
                        if("search".equalsIgnoreCase(modules.getString("module_name"))) {
                            usageCategory = "Database";
                            search = "TRUE";
                        }
                    }
                }
                catch(Exception e) {}


                
                record = record + "," + bdb.getString("name") + "," + bdb.getString("version") + "," + usageCategory + ","
                        + bdb.getInt("memory_size")  + "," + search + "," + bdb.getString("data_persistence") + "," + bdb.getBoolean("replication") + "," + bdb.getBoolean("sharding") + ","
                        + bdb.getInt("shards_count") + "," + bdb.getBoolean("backup") +  "," + bdb.getString("eviction_policy") + "," + bdb.getBoolean("bigstore") + ","
                        + bdb.getBoolean("crdt") + "," + bdb.getString("crdt_guid");

                pw.println(record);
            }
            
        }

        pw.close();
    }


    static void disableCertValidaton() {

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {

                new X509TrustManager() {

                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (GeneralSecurityException e) {
        }
    }

    /**
     * Says hello to the world.
     * 
     * @param args The arguments of the program.
     */
    public static void main(String[] args) throws Exception {

        // For dev purposes only
        disableCertValidaton();

        if (args.length > 0) {
            System.out.println(
                    "Description: This script leverages the Redis Rest API to report on the DB configuration within one or more Redis Enterprise Clusters.\n");
            System.out.println("Usage:\njava -cp BDB-Usage.jar App ./cluster-config.json");
        } else {
            App bdb = new App();
            System.out.println("");
            bdb.loadConfigFile("./sample_test_config.json");
            bdb.saveClusterDBDetails();
            bdb.setBasicAuthString("jay.dastur@redis.com", "RedisGeek");
            bdb.executeGet("https://node1.jsd.demo.redislabs.com:9443/v1/cluster?fields=name");

        }

    }
}
