/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.github.sakserv.minicluster.impl;

import com.github.sakserv.minicluster.MiniCluster;
import com.github.sakserv.minicluster.util.FileUtils;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStore;
import org.apache.hadoop.hive.metastore.txn.TxnDbUtil;
import org.apache.hadoop.hive.thrift.HadoopThriftAuthBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.Permission;

public class HiveLocalMetaStore implements MiniCluster {

    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(HiveLocalMetaStore.class);

    private String hiveMetastoreHostname;
    private Integer hiveMetastorePort;
    private String hiveMetastoreDerbyDbDir;
    private String hiveScratchDir;
    private String hiveWarehouseDir;
    private HiveConf hiveConf;
    
    private static SecurityManager securityManager;
    private Thread t;
    
    private HiveLocalMetaStore(Builder builder) {
        this.hiveMetastoreHostname = builder.hiveMetastoreHostname;
        this.hiveMetastorePort = builder.hiveMetastorePort;
        this.hiveMetastoreDerbyDbDir = builder.hiveMetastoreDerbyDbDir;
        this.hiveScratchDir = builder.hiveScratchDir;
        this.hiveWarehouseDir = builder.hiveWarehouseDir;
        this.hiveConf = builder.hiveConf;
    }

    public String getHiveMetastoreHostname() {
        return hiveMetastoreHostname;
    }
    
    public Integer getHiveMetastorePort() {
        return hiveMetastorePort;
    }

    public String getHiveMetastoreDerbyDbDir() {
        return hiveMetastoreDerbyDbDir;
    }

    public String getHiveScratchDir() {
        return hiveScratchDir;
    }

    public HiveConf getHiveConf() {
        return hiveConf;
    }
    
    public String getHiveWarehouseDir() {
        return hiveWarehouseDir;
    }
    
    public static class Builder {
        private String hiveMetastoreHostname;
        private Integer hiveMetastorePort;
        private String hiveMetastoreDerbyDbDir;
        private String hiveScratchDir;
        private String hiveWarehouseDir;
        private HiveConf hiveConf;
        
        public Builder setHiveMetastoreHostname(String hiveMetastoreHostname) {
            this.hiveMetastoreHostname = hiveMetastoreHostname;
            return this;
        }
        
        public Builder setHiveMetastorePort(int hiveMetaStorePort) {
            this.hiveMetastorePort = hiveMetaStorePort;
            return this;
            
        }
        
        public Builder setHiveMetastoreDerbyDbDir(String hiveDerbyDbDir) {
            this.hiveMetastoreDerbyDbDir = hiveDerbyDbDir;
            return this;
        }
        
        public Builder setHiveScratchDir(String hiveScratchDir) {
            this.hiveScratchDir = hiveScratchDir;
            return this;
        }

        public Builder setHiveConf(HiveConf hiveConf) {
            this.hiveConf = hiveConf;
            return this;
        }
        
        public Builder setHiveWarehouseDir(String hiveWarehouseDir) {
            this.hiveWarehouseDir = hiveWarehouseDir;
            return this;
        }
        
        public HiveLocalMetaStore build() {
            HiveLocalMetaStore hiveLocalMetaStore = new HiveLocalMetaStore(this);
            validateObject(hiveLocalMetaStore);
            return hiveLocalMetaStore;
        }
        
        public void validateObject(HiveLocalMetaStore hiveLocalMetaStore) {
            if(hiveLocalMetaStore.hiveMetastoreHostname == null) {
                throw new IllegalArgumentException("ERROR: Missing required config: Hive Meta Store Hostname");
            }
            
            if(hiveLocalMetaStore.hiveMetastorePort == null) {
                throw new IllegalArgumentException("ERROR: Missing required config: Hive Meta Store Port");
            }

            if(hiveLocalMetaStore.hiveMetastoreDerbyDbDir == null) {
                throw new IllegalArgumentException("ERROR: Missing required config: Hive Derby Db Path");
            }

            if(hiveLocalMetaStore.hiveScratchDir == null) {
                throw new IllegalArgumentException("ERROR: Missing required config: Hive Scratch Dir");
            }

            if(hiveLocalMetaStore.hiveWarehouseDir == null) {
                throw new IllegalArgumentException("ERROR: Missing required config: Hive Warehouse Dir");
            }
            
            if(hiveLocalMetaStore.hiveConf == null) {
                throw new IllegalArgumentException("ERROR: Missing required config: Hive Conf");
            }
            
        }
        
    }

    public class NoExitSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission perm) {
            // allow anything.
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            // allow anything.
        }

        @Override
        public void checkExit(int status) {

            super.checkExit(status);
        }
    }

    private static class StartHiveLocalMetaStore implements Runnable {
        
        private Integer hiveMetastorePort;
        private HiveConf hiveConf;
        
        public void setHiveMetastorePort(Integer hiveMetastorePort) {
            this.hiveMetastorePort = hiveMetastorePort;
        }
        
        public void setHiveConf(HiveConf hiveConf) {
            this.hiveConf = hiveConf;
        }

        @Override
        public void run() {
            try {
                HiveMetaStore.startMetaStore(hiveMetastorePort, 
                        new HadoopThriftAuthBridge(), 
                        hiveConf);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
    
    
    
    public void configure() {
        securityManager = System.getSecurityManager();
        System.setSecurityManager(new NoExitSecurityManager());
        hiveConf.setVar(HiveConf.ConfVars.METASTOREURIS, 
                "thrift://" + hiveMetastoreHostname + ":" + hiveMetastorePort);
        hiveConf.set(HiveConf.ConfVars.SCRATCHDIR.varname, hiveScratchDir);
        hiveConf.set(HiveConf.ConfVars.METASTORECONNECTURLKEY.varname, 
                "jdbc:derby:;databaseName=" + hiveMetastoreDerbyDbDir + ";create=true");
        hiveConf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, new File(hiveWarehouseDir).getAbsolutePath());
        hiveConf.setBoolVar(HiveConf.ConfVars.HIVE_IN_TEST, true);
    }

    public void stop() {
        LOG.info("HIVESERVER2: Stopping Hive Metastore on port: " + hiveMetastorePort);
        stop(true);
    }

    public void stop(boolean cleanUp) {
        if(cleanUp) {
            cleanDb();
        }
        
        t.interrupt();
        
        if (cleanUp) {
            cleanUp();
        }
    }

    private void cleanUp() {
        FileUtils.deleteFolder(hiveMetastoreDerbyDbDir);
        FileUtils.deleteFolder(new File("derby.log").getAbsolutePath());
    }

    public void start() {
        LOG.info("HIVESERVER2: Starting Hive Metastore on port: " + hiveMetastorePort);
        StartHiveLocalMetaStore startHiveLocalMetaStore = new StartHiveLocalMetaStore();
        startHiveLocalMetaStore.setHiveMetastorePort(hiveMetastorePort);
        startHiveLocalMetaStore.setHiveConf(hiveConf);
        t = new Thread(startHiveLocalMetaStore);
        t.start();
        try {
            Thread.sleep(5000);
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        prepDb();
    }

    public void prepDb() {
        try {
            LOG.info("HIVE METASTORE: Prepping the database");
            TxnDbUtil.setConfValues(hiveConf);
            TxnDbUtil.prepDb();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void cleanDb() {
        try {
            LOG.info("HIVE METASTORE: Cleaning up the database");
            TxnDbUtil.setConfValues(hiveConf);
            TxnDbUtil.cleanDb();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
