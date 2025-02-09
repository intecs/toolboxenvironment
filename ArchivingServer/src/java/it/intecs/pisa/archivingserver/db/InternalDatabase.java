/*
 *  Copyright 2009 Intecs Informatica e Tecnologia del Software.
 * 
 *  Licensed under the GNU GPL, version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.gnu.org/copyleft/gpl.html
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package it.intecs.pisa.archivingserver.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author Massimiliano Fanciulli
 */
public class InternalDatabase {
    public static final String ERROR_CANNOT_CONNECT_TO_DB="Cannot connect to Internal Database";
    public static final String ERROR_CANNOT_CREATE_STATEMENT="Cannot create a statement for executing query on the Internal Database";
    public static final String ERROR_NOT_INITED="Not inited. Path to Internal Database has not been set.";
    public static final String ERROR_CANNOT_SHUTDOWN="Cannot shutdown Internal Database. An error occurred.";

    protected static final String driver = "org.hsqldb.jdbcDriver";

    protected static InternalDatabase instance=new InternalDatabase();
    private String databasePath;
    protected Connection conn=null;
    protected boolean isConnected=false;
    protected boolean isInited=false;

    protected InternalDatabase()
    {
        try
        {
            Class.forName(driver);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public static InternalDatabase getInstance()
    {
        return instance;
    }

    public Statement getStatement() throws Exception
    {
        Statement stm;
        try {
            if(isConnected==false)
                connect();

            stm = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            return stm;
        } catch (SQLException ex) {
            throw new Exception(ERROR_CANNOT_CREATE_STATEMENT,ex);
        }
     }

    protected void connect() throws Exception
    {
        String jdbcConnectString;
        try
        {
            if(isInited==false)
                throw new Exception(ERROR_NOT_INITED);

            jdbcConnectString="jdbc:hsqldb:"+databasePath+";";

            conn = DriverManager.getConnection(jdbcConnectString,"ARCHUSR","intecs");
            conn.setAutoCommit(true);
            isConnected=true;
        }
        catch(Exception e)
        {
            isConnected=false;
            throw new Exception(ERROR_CANNOT_CONNECT_TO_DB,e);
        }
    }

    public void close() throws Exception
    {
        try
        {
            if(isConnected==true)
                conn.close();
        }
        catch(Exception e)
        {
            throw new Exception(ERROR_CANNOT_SHUTDOWN,e);
        }
    }

    public boolean isConnected()
    {
        return isConnected;
    }

    public boolean isInited()
    {
        return isInited;
    }

    /**
     * @return the databasePath
     */
    public String getDatabasePath() {
        return databasePath;
    }

    /**
     * @param databasePath the databasePath to set
     */
    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
        isInited=true;
    }
}
