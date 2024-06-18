Java Application (Java 1.8) to report on the consumption of RE Clusters.

To Execute:

#1 Create a config file based on the sample_test_config.json file. e.g. cluster_config.json

#2 Donwload the jar file in the target folder bdb-v-1.0-jar-with-dependencies.jar

#3 Upload both the jar file and the cluster_config.json file to the VM on which you will execute the script.

#4 java -cp ./bdb-v-1.0-jar-with-dependencies.jar com.redis.r2a2.App ./cluster_config.json

#5 The script will output the results to a file called bdb-report.csv. Send this file back to your SA
