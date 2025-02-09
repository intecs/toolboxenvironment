/*
 *
 * ****************************************************************************
 *  Copyright 2003*2010 Intecs
 ****************************************************************************
 *  This file is part of TOOLBOX.
 *
 *  TOOLBOX is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  TOOLBOX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS companyNameFOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with TOOLBOX; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 ****************************************************************************
 *  File Name:         $RCSfile: Toolbox.java,v $
 *  TOOLBOX Version:   $Name: HEAD $
 *  File Revision:     $Revision: 1.1.1.1 $
 *  Revision Date:     $Date: 2006/06/13 15:02:26 $
 *
 */
package it.intecs.pisa.toolbox;

import it.intecs.pisa.toolbox.util.Util;
import be.kzen.ergorr.service.RepositoryManager;
import com.google.gson.JsonObject;
import it.intecs.pisa.common.tbx.Interface;
import it.intecs.pisa.toolbox.service.TBXOperation;
import it.intecs.pisa.toolbox.service.TBXService;
import it.intecs.pisa.common.communication.ServerDebugConsole;
import it.intecs.pisa.common.communication.messages.ExecutionStartedMessage;
import it.intecs.pisa.common.communication.messages.TerminateMessage;
import it.intecs.pisa.soap.toolbox.exceptions.ToolboxException;
import it.intecs.pisa.toolbox.configuration.ToolboxConfiguration;
import java.io.*;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.xml.namespace.QName;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.apache.log4j.*;
import java.net.*;
import it.intecs.pisa.util.*;
import it.intecs.pisa.toolbox.service.ServiceManager;
import it.intecs.pisa.toolbox.db.ToolboxInternalDatabase;
import it.intecs.pisa.pluginscore.IManagerPlugin;
import it.intecs.pisa.pluginscore.IRESTManagerPlugin;
import it.intecs.pisa.pluginscore.InterfacePluginManager;
import it.intecs.pisa.pluginscore.ManagerPluginManager;
import it.intecs.pisa.pluginscore.TagPluginManager;
import it.intecs.pisa.toolbox.resources.LogResourcesPersistence;
import it.intecs.pisa.toolbox.resources.XMLResourcesPersistence;
import it.intecs.pisa.toolbox.db.ServiceStatuses;
import it.intecs.pisa.pluginscore.UIPluginManager;
import it.intecs.pisa.toolbox.cleanup.AutomaticCleanup;
import it.intecs.pisa.toolbox.constants.MiscConstants;
import it.intecs.pisa.toolbox.constants.RequestsConstants;
import it.intecs.pisa.toolbox.constants.ToolboxFoldersFileConstants;
import it.intecs.pisa.toolbox.constants.XMLConstants;
import it.intecs.pisa.toolbox.db.StatisticsUtil;
import it.intecs.pisa.toolbox.log.ErrorMailer;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.http.AxisServlet;
import org.apache.axis2.util.XMLUtils;
import it.intecs.pisa.toolbox.plugins.managerNativePlugins.DeployServiceCommand;
import it.intecs.pisa.toolbox.resources.TextResourcesPersistence;
import it.intecs.pisa.toolbox.timers.AsynchInstancesSecondScriptWakeUpManager;
import it.intecs.pisa.toolbox.timers.PushRetryManager;
import it.intecs.pisa.toolbox.timers.TimeoutManager;
import it.intecs.pisa.toolbox.timers.TimerManager;
import it.intecs.pisa.util.json.JsonErrorObject;
import it.intecs.pisa.util.json.JsonUtil;
import it.intecs.pisa.util.xml.DocumentError;

public class Toolbox extends AxisServlet implements ServletContextListener {
    private static String mailError = null;
    private File rootDir;
    private File logDir;
    private File logFile;
    private Logger logger;
    private FTPServerManager ftpServerManager;
    private boolean configurationProcessed;
    private Object processingRequest;
    private static boolean isDbRunning = false;
    private long instanceKeyUnderDebug = -1;
    private ServerDebugConsole dbgConsole;
    private TagPluginManager tagPluginManager;
    private InterfacePluginManager interfacePluginManager;
    private ManagerPluginManager managerPluginManager;
    private ToolboxInternalDatabase internalDatabase;
    private XMLResourcesPersistence xmlResPersistence;
    private LogResourcesPersistence logResPersistence;
    protected ServiceManager serviceManager;
    protected UIPluginManager uiPluginManager;
    private String deployAdminToken;
    private boolean needsInitialization = true;
    private ToolboxConfiguration tbxConfig;
    private String toolboxVersion = "";
    private String toolboxRevision = "";
    /**
     *  Method accessed by the administration WEB application jsp pages, to retrieve the {@link Toolbox.ToolboxConfigurator} object.
     */
    private static Toolbox servletInstance;

    public static Toolbox getInstance() {
        return servletInstance;
    }

    public ServerDebugConsole initDebugConsole(int port) {

        try {
            if (dbgConsole != null) {
                TerminateMessage msg;

                msg = new TerminateMessage();
                dbgConsole.sendCommand(msg);
                dbgConsole.close();
            }

            dbgConsole = new ServerDebugConsole(port);
            dbgConsole.start();
            return dbgConsole;



        } catch (Exception e) {
            dbgConsole = null;
            return null;
        }
    }

    public void signalDebugConsoleClosed() {
        dbgConsole.close();
        dbgConsole = null;
        this.instanceKeyUnderDebug = -1;
        System.gc();
    }

    public void initFtpServer(File webinf) {
        String dir = null;
        String warnMsg = null;

        try {
            dir = (new File(webinf, ToolboxFoldersFileConstants.FTP_SERVER)).getAbsolutePath();

            setFtpServerManager(FTPServerManager.getInstance(dir));

            ftpServerManager.updatePort(tbxConfig.getConfigurationValue(ToolboxConfiguration.FTP_PORT));
            ftpServerManager.updatePassiveModePort(tbxConfig.getConfigurationValue(ToolboxConfiguration.FTP_POOL_PORT));
            ftpServerManager.updateServerHost(tbxConfig.getConfigurationValue(ToolboxConfiguration.FTP_SERVER_HOST));

            ftpServerManager.startServer(dir);
        } catch (UnknownHostException ex) {
            warnMsg = "The Host provided for the FTP server is unknown";
        } catch (IOException ex) {
            warnMsg = "I/O problem during FTP server startup";
        } catch (Exception ex) {
            warnMsg = "The startup of the FTP server raised the following exception: " + ex.getCause();
        }

        if (getFtpServerManager() == null || getFtpServerManager().isServerRunning() == false) {
            warnMsg = "Couldn't start FTP server: address already in use";
        }

        if (warnMsg != null) {
            if (logger != null) {
                logger.log(Level.WARN, warnMsg);
            } else {
                System.out.println(warnMsg);
            }
        }
    }

    protected void executeRestCommandJSON(HttpServletRequest req, HttpServletResponse resp, IRESTManagerPlugin commandPlugin, String formatLessCmd) throws IOException, Exception {
        JsonObject inputObj;
        inputObj = JsonUtil.getInputAsJson(req.getInputStream());
        JsonObject jsonResp;
        boolean res = commandPlugin.authenticate(formatLessCmd, inputObj);
        if (res == true) {
            res = commandPlugin.validateInput(formatLessCmd, inputObj);
            if (res == true) {
                jsonResp = commandPlugin.executeCommand(formatLessCmd, inputObj);
            } else {
                jsonResp = JsonErrorObject.get("Could not validate input");
            }
        } else {
            jsonResp = JsonErrorObject.get("Could not authenticate request");
        }
        resp.setHeader("Content-Type", "text/json");
        JsonUtil.writeJsonToStream(jsonResp, resp.getOutputStream());
    }

    protected void executeRestCommandStream(HttpServletRequest req, HttpServletResponse resp, IRESTManagerPlugin commandPlugin, String formatLessCmd) throws IOException {
        InputStream response;
        boolean res = commandPlugin.authenticate(formatLessCmd, req.getInputStream());
        if (res == true) {
            res = commandPlugin.validateInput(formatLessCmd, req.getInputStream());
            if (res == true) {
                response = commandPlugin.executeCommand(formatLessCmd, req.getInputStream());
            } else {
                response = new ByteArrayInputStream("Could not validate input".getBytes());
            }
        } else {
            response = new ByteArrayInputStream("Could not authenticate request".getBytes());
        }
        IOUtil.copy(response, resp.getOutputStream());
    }

    protected void executeRestCommandXML(HttpServletRequest req, HttpServletResponse resp, IRESTManagerPlugin commandPlugin, String formatLessCmd) throws Exception {
        DOMUtil util;
        Document inputDoc;
        try {
            util = new DOMUtil();
            inputDoc = util.inputStreamToDocument(req.getInputStream());
        } catch (Exception e) {
            inputDoc = null;
        }
        Document respDoc;
        boolean res = commandPlugin.authenticate(formatLessCmd, inputDoc);
        if (res == true) {
            res = commandPlugin.validateInput(formatLessCmd, inputDoc);
            if (res == true) {
                respDoc = commandPlugin.executeCommand(formatLessCmd, inputDoc);
            } else {
                respDoc = DocumentError.get("Could not validate input");
            }
        } else {
            respDoc = DocumentError.get("Could not authenticate request");
        }
        resp.setHeader("Content-Type", "text/xml");
        DOMUtil.dumpXML(respDoc, resp.getOutputStream(), false);
    }

    protected void setOutputHeaders(IRESTManagerPlugin commandPlugin, HttpServletResponse resp) {
        Map<String, String> outputHeaders = null;
        outputHeaders = commandPlugin.getOutputHeaders();
        if (outputHeaders != null) {
            Set<String> keys;
            keys = outputHeaders.keySet();
            for (String key : keys.toArray(new String[0])) {
                resp.setHeader(key, outputHeaders.get(key));
            }
        }
    }

    private void executeRestCommandServletInterface(HttpServletRequest req, HttpServletResponse resp, IRESTManagerPlugin commandPlugin) throws Exception {
        DOMUtil util;
        InputStream errorResp = null;

        boolean res = commandPlugin.authenticate(req, resp);
        if (res == true) {
            res = commandPlugin.validateInput(req, resp);
            if (res == true) {
                commandPlugin.executeCommand(req, resp);
            } else {
                errorResp = new ByteArrayInputStream("Could not validate input".getBytes());
            }
        } else {
            errorResp = new ByteArrayInputStream("Could not authenticate request".getBytes());
        }

        if (errorResp != null) {
            IOUtil.copy(errorResp, resp.getOutputStream());
        }
    }

    private void debugServiceRequest(HttpServletResponse resp, HttpServletRequest req, String requestURI) throws IOException {
        int index = 0;
        String token;
        String serviceToDebug;
        String operationToDebug;
        String newUri;

        index = requestURI.lastIndexOf("/");
        token = requestURI.substring(index + 1);

        if (dbgConsole != null) {
            dbgConsole.waitForConnection();

            if (dbgConsole.isConnected() && dbgConsole.getDebugToken() != null && dbgConsole.getDebugToken().equals(token)) {
                serviceToDebug = dbgConsole.getServiceToDebug();
                operationToDebug = dbgConsole.getOperationToDebug();

                System.out.println("Starting debug of operation " + operationToDebug + " for service " + serviceToDebug);

                index = requestURI.indexOf("TOOLBOX");
                newUri = requestURI.substring(0, index);
                newUri += "TOOLBOX/services/" + serviceToDebug;


                dbgConsole.sendCommand(new ExecutionStartedMessage());
                executeServiceRequest(resp, req, newUri, true);



            } else {
                resp.sendError(500);
            }
        } else {
            resp.sendError(500);
        }
    }

    private void executeManagerCommands(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String cmd;
        String method;
        ManagerPluginManager man;
        IManagerPlugin commandPlugin;
        try {
            cmd = req.getParameter(RequestsConstants.REQUEST_PARAMETER_COMMAND);
            method = req.getMethod();

            man = ManagerPluginManager.getInstance();

            commandPlugin = man.getCommand(cmd, method);
            commandPlugin.executeCommand(req, resp);
            resp.setStatus(resp.SC_OK);
        } catch (Exception e) {
            e.printStackTrace(System.out);
            resp.sendError(resp.SC_INTERNAL_SERVER_ERROR);
        }

    }

    private void executeServiceRequest(HttpServletResponse resp, HttpServletRequest req, String requestURI, boolean debugMode) throws IOException {
        Document doc = null;
        Document responseDocument = null;
        Document soapRequestDocument = null;
        String errorMsg = null;
        PrintWriter writer = null;
        DOMUtil domUtil = null;
        String soapaction = null;

        try {

            resp.setContentType("text/xml");
            writer = resp.getWriter();
            domUtil = new DOMUtil(true);

            try {
                soapRequestDocument =   domUtil.inputStreamToDocument(req.getInputStream());
            } catch (Exception e) {
                errorMsg = "Error extracting SOAP payload: " + MiscConstants.CDATA_S + e.getMessage() + MiscConstants.CDATA_E;
                logger.error(errorMsg);
                throw new ToolboxException(errorMsg);
            }
            //TODO this only works for SOAP1.1
            soapaction = req.getHeader("soapaction").replaceAll("\"", "");
            MessageContext msgCtx = new MessageContext();
            responseDocument = executeServiceRequest(soapaction, soapRequestDocument, requestURI, debugMode);
            try {
                Util.addSOAPEnvelope(responseDocument);
                new XMLSerializer2(writer).serialize(responseDocument);
                writer.flush();
                writer.close();
            } catch (Exception e) {
                errorMsg = "Error while serializing response document:: " + e.getMessage();
                logger.error(errorMsg);
                ErrorMailer.send(null, soapaction, null, null, errorMsg);
                throw new ToolboxException(errorMsg);
            }

        } catch (Exception e) {
            //******** an exception has been thrown, sending a SOAP Fault ********************

            try {
                if (e instanceof ToolboxException) {
                    doc = Util.getSOAPFault((ToolboxException) e);
                } else {
                    doc = Util.getSOAPFault(e.getMessage());
                }
                Util.addSOAPEnvelope(doc);
                new XMLSerializer2(writer).serialize(doc);
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
            writer.close();
        }

    }

    /**
     * Executes the service identified by the given URI and SOAP action and returns the response.
     * 
     * @param soapRequestDocument
     * @param soapAction
     * @param requestURI
     * @param debugMode
     * @return
     * @throws IOException
     * @throws ToolboxException
     */
    public Document executeServiceRequest(MessageContext msgCtx, String requestURI, boolean debugMode) throws IOException, ToolboxException {

        Element soapRequestElement = null;
        try {
            soapRequestElement = XMLUtils.toDOM(msgCtx.getEnvelope());

        } catch (Exception ex) {
            this.logger.error("Error while converting SOAP envelope from OM to DOM", ex);
            throw new ToolboxException("Error while retrieving SOAP envelope, impossible to execute the service request.");
        }

        String operationName = Toolbox.getOperationName(msgCtx);

        return executeServiceRequest(operationName, soapRequestElement.getOwnerDocument(), requestURI, debugMode);
    }

    /**
     *
     * @author Stefano
     * @param operationName
     * @param soapRequestDoc
     * @param requestURI
     * @param debugMode
     * @return
     * @throws IOException
     * @throws ToolboxException
     */
    public Document executeServiceRequest(String operationName, Document soapRequestDoc, String requestURI, boolean debugMode) throws IOException, ToolboxException {

        Document responseDocument = null;
        String errorMsg = null;
        String serviceName = null;
        TBXService service = null;

        serviceName = requestURI.substring(requestURI.lastIndexOf("/") + 1);
        service = (TBXService) this.serviceManager.getService(serviceName);

        TBXOperation operation = (TBXOperation) service.getImplementedInterface().getOperationBySOAPAction(operationName);
        if (operation == null) {
            logger.error("[" + serviceName + "] " + "Unknown SOAP port" + operationName);
            ErrorMailer.send(serviceName, operationName, null, null, "[" + serviceName + "] " + "Unknown SOAP port" + operationName);
            throw new ToolboxException("Unknown SOAP port" + operationName + " for service " + serviceName);
        }

        //**************** Processing Request *********************

        try {
            if (debugMode) {
                System.out.println("Process new Debug instance");
            } else {
                System.out.println("Process new Run instance");
            }
            responseDocument = service.processRequest(operation, soapRequestDoc, debugMode);
            logger.info("[" + serviceName + "] Processing request successful");
        } catch (Exception e) {
            errorMsg = "[" + serviceName + "] " + e.getMessage();
            logger.error(errorMsg);
            throw new ToolboxException(errorMsg);
        }

        return responseDocument;
    }


    /**
     *
     * @author Stefano
     * @param operationName
     * @param soapRequestDoc
     * @param requestURI
     * @param debugMode
     * @return
     * @throws IOException
     * @throws ToolboxException
     */
    public Document executeServiceRequest(Document soapRequestDoc, String requestURI, boolean debugMode) throws IOException, ToolboxException {

        Document responseDocument = null;
        String errorMsg = null;
        String serviceName = null;
        TBXService service = null;

        serviceName = requestURI.substring(requestURI.lastIndexOf("/") + 1);
        service = (TBXService) this.serviceManager.getService(serviceName);
        
        //EXTRACT THE BODY CHILD TAG NAMESPACE AND LOCAL NAME
        Element body = (Element)soapRequestDoc.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body").item(0);
        Node tag = body.getFirstChild();
        String namespace = tag.getNamespaceURI();
        String tagName = tag.getLocalName();
        TBXOperation operation = (TBXOperation) service.getImplementedInterface().getOperationByMainTag(namespace, tagName);

        if (operation == null) {
            logger.error("[" + serviceName + "] " + "Unknown message: the following element is not recognized" + namespace + ":" + tagName);
            ErrorMailer.send(serviceName, tagName, null, null, "[" + serviceName + "] " + "Unknown message: the following element is not recognized" + namespace + ":" + tagName);
            throw new ToolboxException("Unknown message: the following element is not recognized" + namespace + ":" + tagName);
        }

        //**************** Processing Request *********************

        try {
            if (debugMode) {
                System.out.println("Process new Debug instance");
            } else {
                System.out.println("Process new Run instance");
            }
            responseDocument = service.processRequest(operation, soapRequestDoc, debugMode);
            logger.info("[" + serviceName + "] Processing request successful");
        } catch (Exception e) {
            errorMsg = "[" + serviceName + "] " + e.getMessage();
            logger.error(errorMsg);
            throw new ToolboxException(errorMsg);
        }

        return responseDocument;
    }

    private void executeHttpPost(HttpServletRequest req, HttpServletResponse resp, String requestURI) throws IOException {
        Document doc = null;
        Document responseDocument = null;
        Document soapRequestDocument = null;
        String errorMsg = null;
        PrintWriter writer = null;
        DOMUtil domUtil = null;
        String soapaction = null;

        try {
            resp.setContentType("text/xml");
            writer = resp.getWriter();
            domUtil = new DOMUtil(true);

            try {
                BufferedReader buf = req.getReader();
                soapRequestDocument = domUtil.readerToDocument(buf);
            } catch (Exception e) {
                errorMsg = "Error reading the post message content: " + MiscConstants.CDATA_S + e.getMessage() + MiscConstants.CDATA_E;
                logger.error(errorMsg);
                throw new ToolboxException(errorMsg);
            }
            //String operationName = "";

            //HANDLE THE DEBUG_MODE
            //ADD THE SOAP HEADER TO THE POST BODY
            Util.addSOAPEnvelope(soapRequestDocument);

            //GET THE OPERATION NAME
            //operationName = requestURI.substring(requestURI.lastIndexOf("/") + 1);

//            responseDocument = executeServiceRequest(operationName, soapRequestDocument, requestURI.substring(0,requestURI.lastIndexOf("/")), false);
            responseDocument = executeServiceRequest(soapRequestDocument, requestURI, false);

            try {
                new XMLSerializer2(writer).serialize(responseDocument);
                writer.flush();
                writer.close();
            } catch (Exception e) {
                errorMsg = "Error while serializing response document:: " + e.getMessage();
                logger.error(errorMsg);
                ErrorMailer.send(null, soapaction, null, null, errorMsg);
                throw new ToolboxException(errorMsg);
            }

        } catch (Exception e) {
            //******** an exception has been thrown, sending a SOAP Fault ********************

            try {
                if (e instanceof ToolboxException) {
                    doc = Util.getSOAPFault((ToolboxException) e);
                } else {
                    doc = Util.getSOAPFault(e.getMessage());
                }
                Util.addSOAPEnvelope(doc);
                new XMLSerializer2(writer).serialize(doc);
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
            writer.close();
        }
    }

    private void handleGetRepository(HttpServletRequest request, HttpServletResponse response, String serviceName) throws Exception {
        TBXService service;
        ServiceManager servMan;
        Interface interf;
        servMan = ServiceManager.getInstance();
        service = servMan.getService(serviceName);

        interf = service.getImplementedInterface();
        if (interf.getName().equals("OGC-06-131r6") && interf.getVersion().equals("0.2.4") && interf.getType().equals("Catalogue") && interf.getMode().equals("StandAlone")) {



            response.setContentType("text/xml"); // TODO - read from ExtrinsicObject contenttype
            ServletOutputStream out = response.getOutputStream();
            String id = request.getParameter("id");

            if (id != null && !id.trim().equals("")) {
                Hashtable<String, String> var;

                var = service.getImplementedInterface().getUserVariable().get("ebRRDbName");


                RepositoryManager repoMngr = new RepositoryManager(var.get(Interface.VAR_TABLE_VALUE));
                File file = repoMngr.getFile(id);

                logger.info("Checking repo file: " + file.getAbsolutePath());
                if (file.exists()) {
                    logger.info("Request for repo file: " + file.getAbsolutePath());

                    IOUtil.copy(new FileInputStream(file), out);
                } else {
                    response.sendError(404);
                }
            } else {
                System.out.println("ID not provided");
            }
        }


    }

    /**
     *  Used by {@link TBXService} objects to retrieve the {@link #processingRequest} variable
     *  @see #processingRequest
     *  @see #setProcessingRequest
     */
    public Object getProcessingRequest() {
        return processingRequest;
    }

    /**
     *  Used by {@link TBXService} objects to set the {@link #processingRequest} variable
     *  @see #processingRequest
     *  @see #getProcessingRequest
     */
    public void setProcessingRequest(Object processingRequest) {
        this.processingRequest = processingRequest;
    }

    /**
     *  Part of the life cicle of every servlet, it is executed the first time the servlet receives a request.
     *  After calling the corresponding method of the parent class, it loads configuration file and creates the instance of the {@link Toolbox.ToolboxConfigurator}. It doesn't execute the {@link #processConfiguration} method.
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        File pluginDirectory;
        File ebRRpropertiesFile;

        if (this.needsInitialization == false) {
            return;
        }

        try {
            super.init(config);
        } catch (Exception ecc) {
            System.out.println("Axis init Exception!");
            ecc.printStackTrace();
        }

        DOMUtil util;
        String port;
        String sender = null;
        String recipients = null;
        String recipientsSSE = null;
        String smtp = null;
        String tbxVersion = null;
        String companyName = null;
        String companyContact = null;
        try {
            servletInstance = this;
            util = new DOMUtil();

            rootDir = new File(getServletContext().getRealPath(MiscConstants.ROOT));
            File webinfDir = new File(rootDir, ToolboxFoldersFileConstants.WEB_INF);
            File libDir = new File(webinfDir, "lib");
            File workingDir = new File(webinfDir, "db");
            File dblock = new File(workingDir, "TOOLBOX.lck");
            if (dblock.exists()) {
                dblock.delete();
            }

            internalDatabase = ToolboxInternalDatabase.getInstance();
            internalDatabase.setDatabasePath("file:" + workingDir.getAbsolutePath() + File.separatorChar + "TOOLBOX");
            internalDatabase.getStatement();
            ServiceStatuses.removeServiceStatus("testService");
            ServiceStatuses.addServiceStatus("testService");

            tbxConfig = ToolboxConfiguration.getInstance();
            tbxConfig.initializeConfigTable();
            tbxConfig.loadConfiguration();

            File configuredLogDir = new File(tbxConfig.getConfigurationValue(ToolboxConfiguration.LOG_DIR));

            Boolean usingTempDirectory = false;
            String errorMessage = "";
            configuredLogDir.mkdirs();

            if (!configuredLogDir.canWrite() || !configuredLogDir.canRead()) {
                //in case of write permission errors we use thetemporary directory
                errorMessage = "Unable to create log directory: " + configuredLogDir.getAbsolutePath() + " Check the read/write permissions";
                System.out.println(errorMessage);
                tbxConfig.setConfigurationValue(ToolboxConfiguration.LOG_DIR, System.getProperty("java.io.tmpdir"));
                configuredLogDir = new File(System.getProperty("java.io.tmpdir"));
                System.out.println("Setting the Log Directory to: " + System.getProperty("java.io.tmpdir"));
                usingTempDirectory = true;
            }

            File resourcePersistenceDir = new File(configuredLogDir, "XML");

            this.xmlResPersistence = XMLResourcesPersistence.getInstance();
            this.xmlResPersistence.setDirectory(resourcePersistenceDir);

            resourcePersistenceDir = new File(configuredLogDir, "Logs");

            logResPersistence = LogResourcesPersistence.getInstance();
            logResPersistence.setDirectory(resourcePersistenceDir);

            logger = logResPersistence.getRollingLogForToolbox();

            resourcePersistenceDir = new File(configuredLogDir, "Text");
            TextResourcesPersistence textPers = TextResourcesPersistence.getInstance();
            textPers.setDirectory(resourcePersistenceDir);

            logger.info("Core services started");

            if (usingTempDirectory) {
                logger.error(errorMessage);
                logger.warn("Using the default temporary directory: " + System.getProperty("java.io.tmpdir"));
            }

            pluginDirectory = new File(webinfDir, "plugins");
            ebRRpropertiesFile = new File(pluginDirectory, "ebRRPlugin/resources/common.properties");

            System.setProperty("ergorr.common.properties", ebRRpropertiesFile.getAbsolutePath());

            tagPluginManager = TagPluginManager.getInstance();
            interfacePluginManager = InterfacePluginManager.getInstance();
            managerPluginManager = ManagerPluginManager.getInstance();
            serviceManager = ServiceManager.getInstance();
            uiPluginManager = UIPluginManager.getInstance();

            String tbxPluginDirectory = pluginDirectory.getAbsolutePath();

            tagPluginManager.initManager(tbxPluginDirectory);
            interfacePluginManager.initManager(tbxPluginDirectory);
            managerPluginManager.initManager(tbxPluginDirectory);
            uiPluginManager.initManager(tbxPluginDirectory);

            logger.info("Plugins started");

            adjustAllSchemaReferences(); // Adjust the Schema cross references (import and include) paths according to the rootDir

            File wsdlDir;
            if (!(wsdlDir = new File(rootDir, ToolboxFoldersFileConstants.WSDL)).exists()) {
                wsdlDir.mkdir();
            }
            File exportDir;
            if (!(exportDir = new File(rootDir, ToolboxFoldersFileConstants.EXPORT)).exists()) {
                exportDir.mkdir();
            }
            File tmpDir;
            if (!(tmpDir = new File(new File(rootDir, ToolboxFoldersFileConstants.WEB_INF), ToolboxFoldersFileConstants.TMP)).exists()) {
                tmpDir.mkdir();
            }
            File pushDir;
            if (!(pushDir = new File(rootDir, ToolboxFoldersFileConstants.PUSH)).exists()) {
                pushDir.mkdir();
            }

            processConfiguration();

            serviceManager.setServicesRootDir(new File(rootDir, "WEB-INF/services"));
            serviceManager.setLogger(this.logger);
            serviceManager.initServices();
            //serviceManager.startServices();
            logger.info("Toolbox services started");

            if (ftpServerManager != null) {
                initFtpServer(new File(rootDir, ToolboxFoldersFileConstants.WEB_INF));
                ftpServerManager.updatePort(tbxConfig.getConfigurationValue(ToolboxConfiguration.FTP_PORT));
                logger.info("FTP server started");
            }

            needsInitialization = false;

        } catch (Exception e) {

            throw new ServletException(e);
        }

        try {
            Statement stm = null;
            ToolboxInternalDatabase db;

            db = ToolboxInternalDatabase.getInstance();

            stm = db.getStatement();
            stm.executeUpdate("UPDATE T_SERVICE_INSTANCES SET STATUS=8 WHERE STATUS=16");
            stm.close();
        } catch (Exception e) {
        }

        try {
            // WSILBuilder.createWSIL();

            ServiceManager servMan;
            TBXService[] services;

            servMan = ServiceManager.getInstance();
            services = servMan.getServicesAsArray();

            for (TBXService service : services) {
                service.attemptToDeployWSDLAndSchemas();
            }
        } catch (Exception ecc) {
            logger.error("Error while deploying the WSDL files. Error: " + ecc.getMessage());
        }

        try {
            initVersionRevision();
        } catch (Exception e) {
            this.toolboxRevision = "0";
            this.toolboxVersion = "8.0";
        }

        try {
            AutomaticCleanup.start();

            TimerManager timerMan = TimerManager.getInstance();
            AsynchInstancesSecondScriptWakeUpManager wakeupMan = AsynchInstancesSecondScriptWakeUpManager.getInstance();
            PushRetryManager pushMan = PushRetryManager.getInstance();
            TimeoutManager timeoutMan = TimeoutManager.getInstance();
        } catch (Exception e) {
            logger.info("Cannot start automatic cleanup service");
        }

        logger.info("Initialization completed");
    }

    /**
     *  The loaded configuration parameters are taken into account. Namely, the logger is started, the {@link #queueMutex} is set, the {@link #ftpServerManager} is created and started and the {@link TBXService} table is filled. This method is called at the first invocation of the {@link #onMessage} method or upon any call to {@link Toolbox.ToolboxConfigurator} methods needing the configuration parameter to be processed.
     */
    public void processConfiguration() throws Exception {
        String proxyPort = null;
        String proxyHost;

        if (isConfigurationProcessed()) {
            return;
        }

        logger = LogResourcesPersistence.getInstance().getRollingLogForToolbox();

        logger.info("TOOLBOX START");
        logger.info("configuration validated and loaded");

        proxyHost = tbxConfig.getConfigurationValue(ToolboxConfiguration.PROXY_HOST);
        proxyPort = tbxConfig.getConfigurationValue(ToolboxConfiguration.PROXY_PORT);

        if (proxyHost.length() > 0 && proxyPort.length() > 0) {
            System.setProperty(MiscConstants.PROXY_HOST_KEY, proxyHost);
            System.setProperty(MiscConstants.PROXY_PORT_KEY, proxyPort);
            logger.info("set proxy " + proxyHost + "(port " + proxyPort + ")");
        } else {
            Properties properties = System.getProperties();
            if (properties.containsKey(MiscConstants.PROXY_HOST_KEY)) {
                properties.remove(MiscConstants.PROXY_HOST_KEY);
            }
            if (properties.containsKey(MiscConstants.PROXY_PORT_KEY)) {
                properties.remove(MiscConstants.PROXY_PORT_KEY);
            }
        }

        /* The queueMutex is initialized */
        if (Boolean.parseBoolean(tbxConfig.getConfigurationValue(ToolboxConfiguration.QUEUING))) {
            this.serviceManager.enableGlobalQueuing();
        }

        if (getFtpServerManager() == null) {
            // We try to activate the FTP server.
            try {
                setFtpServerManager(FTPServerManager.getInstance(new File(new File(new File(getServletContext().getRealPath(MiscConstants.ROOT)), ToolboxFoldersFileConstants.WEB_INF), ToolboxFoldersFileConstants.FTP_SERVER).getAbsolutePath()));
            } catch (Exception e) {
                setFtpServerManager(null);
                logger.error("Unable to create the FTP server. Error details: " + e.getMessage());
                logger.error("The FTP server functionalities will be disabled.");
            }

        }

        /* This flag is to avoid doing this process more than once */
        setConfigurationProcessed(true);
    }

    /**
     *  Invoked by the index.jsp as start page of the WEB application. It makes sure that the {@link #init} method has been invoked and hence the {@link Toolbox.ToolboxConfigurator} is available to the other JSP pages. It actually redirect on a static HTML file.
     */
    @SuppressWarnings("empty-statement")
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
        String webAppStart = "TOOLBOX/services/";
        String serviceName;
        String id;
        String service;
        String request;
        String requestURI;

        try {
            requestURI = req.getRequestURI();

            logger.debug("Received request " + requestURI);


            if (requestURI.startsWith("/TOOLBOX/rest")) {
                executeRestCommand(req, resp);
            }

            if (requestURI.startsWith("/TOOLBOX/http")) {
                StatisticsUtil.incrementStatistic(StatisticsUtil.STAT_ARRIVED);
                //executeHttpGet(req, resp);
                return;
            }

            id = req.getParameter("id");
            service = req.getParameter("service");
            request = req.getParameter("request");

            if (id != null && id.equals("") == false && service != null && service.equals("CSW-ebRIM") && request != null && request.equals("GetRepositoryItem")) {
                handleGetRepository(req, resp, getTargetedServiceName(req));
            } else {
                requestURI = req.getRequestURI();
                if (requestURI.contains(webAppStart)) {
                    resp.sendRedirect("../viewServiceInfo.jsp?serviceName=" + getTargetedServiceName(req));
                } else if (requestURI.startsWith("/TOOLBOX/manager")) {
                    executeManagerCommands(req, resp);
                }

            }

        } catch (Exception e) {
            resp.sendError(500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        if (requestURI.startsWith("/TOOLBOX/rest")) {
            executeRestCommand(request, response);
        }
    }

    protected String getTargetedServiceName(HttpServletRequest req) {
        String requestURI = req.getRequestURI();
        StringTokenizer tokenizer = new StringTokenizer(requestURI, "/?");
        while (tokenizer.nextToken().equals("services") == false) {
            ;
        }
        return tokenizer.nextToken();
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestURI;

        requestURI = request.getRequestURI();


        if (requestURI.startsWith("/TOOLBOX/rest")) {
            executeRestCommand(request, response);
        }
    }

    /**
     *  This method invokes the implementation of the super class, unless a "password" parameter is present, in which case it calls the {@link #doGet} method.
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
        String requestURI;

        requestURI = req.getRequestURI();

        logger.debug("Received request " + requestURI);

        if (req.getParameter("password") != null) {
            doGet(req, resp);
        } else if (requestURI.startsWith("/TOOLBOX/services")) {
            StatisticsUtil.incrementStatistic(StatisticsUtil.STAT_ARRIVED);
            super.doPost(req, resp);
            return;
        } else if (requestURI.startsWith("/TOOLBOX/http")) {
            StatisticsUtil.incrementStatistic(StatisticsUtil.STAT_ARRIVED);
//            super.doPost(req, resp);
            executeHttpPost(req, resp, requestURI);
            return;
        }
        if (requestURI.startsWith("/TOOLBOX/debug")) {
            debugServiceRequest(resp, req, requestURI);
        } else if (requestURI.startsWith("/TOOLBOX/deploy")) {
            try {
                executeDeploy(req, resp);
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Toolbox.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
        } else if (requestURI.startsWith("/TOOLBOX/manager")) {
            executeManagerCommands(req, resp);
        } else if (requestURI.startsWith("/TOOLBOX/rest")) {
            executeRestCommand(req, resp);
        }

    }

    /**
     *  Returns the rootDir directory of the TOOLBOX WEB application
     */
    public File getRootDir() {
        return rootDir;
    }

    /**
     *  Returns the log directory
     */
    public File getLogDir() {
        return logDir;
    }

    /**
     *  Builds the public directory of a TBXService, given its name
     */
    public File getPublicServiceDir(String serviceName) {
        return new File(new File(getRootDir(), ToolboxFoldersFileConstants.WSDL), serviceName);
    }

    /**
     *  Builds the work directory of a TBXService, given its name
     */
    public File getServiceRoot(String serviceName) {
        return new File(new File(new File(getRootDir(), ToolboxFoldersFileConstants.WEB_INF), ToolboxFoldersFileConstants.SERVICES), serviceName);
    }

    /**
     *  returns the directory where the axis services are deployed (configured in the axis2.xml)
     */
    public File getAxis2ServicesRoot(String fileName) {
        return new File(new File(new File(getRootDir(), ToolboxFoldersFileConstants.WEB_INF), ToolboxFoldersFileConstants.AXIS2SERVICES), fileName);
    }

    /**
     *  Adjusts the Schema cross references (import and include) paths according to the rootDir in every schema file in the rootDir directory
     */
    private void adjustAllSchemaReferences() throws Exception {
        File[] schemas = new File(new File(getRootDir(), ToolboxFoldersFileConstants.WEB_INF), ToolboxFoldersFileConstants.SCHEMAS).listFiles(new FileFilter() {

            public boolean accept(File file) {
                return file.getName().endsWith(".xsd");
            }
        });
        for (int index = 0; index < schemas.length; adjustSchemaReferences(schemas[index++]));
    }

    /**
     *  Adjusts the Schema cross references (import and include) paths according to the rootDir in a given schema file. It uses {@link #getSchemaLocation}
     */
    private void adjustSchemaReferences(File schemaFile) throws Exception {

        /* Parses the Schema file */
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        Document schemaDocument = documentBuilderFactory.newDocumentBuilder().parse(schemaFile);

        /* Looks for every import element */
        Element schemaElement = schemaDocument.getDocumentElement();
        String schemaPrefix = schemaElement.getPrefix();
        Iterator elements = DOMUtil.getChildrenByTagName(schemaElement, schemaPrefix + ':' + XMLConstants.IMPORT).iterator();

        /* For each import element adjust schema location */
        Element element;
        while (elements.hasNext()) {
            element = (Element) elements.next();
            element.setAttribute(XMLConstants.SCHEMA_LOCATION, getSchemaLocation(element.getAttribute(XMLConstants.SCHEMA_LOCATION)));
        }

        /* Looks for every include element */
        elements = DOMUtil.getChildrenByTagName(schemaElement, schemaPrefix + ':' + XMLConstants.INCLUDE).iterator();

        /* For each include element adjust schema location */
        while (elements.hasNext()) {
            element = (Element) elements.next();
            element.setAttribute(XMLConstants.SCHEMA_LOCATION, getSchemaLocation(element.getAttribute(XMLConstants.SCHEMA_LOCATION)));
        }

        /* Saves file */
        FileOutputStream out = new FileOutputStream(schemaFile);
        new XMLSerializer2(out).serialize(schemaDocument);
        out.close();
    }

    /**
     *  Builds the correct schema location, based on the old value and the current rootDir directory. It uses {@link Util#getURI}.
     */
    private String getSchemaLocation(String oldSchemaLocation) {
        int slashIndex = oldSchemaLocation.lastIndexOf(MiscConstants.SLASH); // It is correct to search for SLASH since it is an URI

        return Util.getURI(new File(new File(new File(getRootDir(), ToolboxFoldersFileConstants.WEB_INF), ToolboxFoldersFileConstants.SCHEMAS), oldSchemaLocation.substring(slashIndex + 1)).getAbsolutePath());
    }

    /**
     *  Part of the life cicle of the Servlet, it is called when reloading the WEB application or when shutting down TOMCAT. It calls {@link #reset} and sets {@link #toolboxConfigurator} to null.
     */
    @Override
    public void destroy() {
        try {


            System.out.println("Stopping all services");
            if (serviceManager != null) {
                serviceManager.tearDownServices();
            }
            System.out.println("Shutting down FTP server");
            if (ftpServerManager != null) {
                ftpServerManager.stopServer();
            }

            System.out.println("Shutting down DB");
            if (isDbRunning) {
                try {
                    internalDatabase.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }

            System.out.println("Shutting down debug console");
            if (dbgConsole != null) {
                dbgConsole.close();

            }

            try {
                AutomaticCleanup.stop();
            } catch (Exception e) {
            }

            TimerManager timerMan = TimerManager.getInstance();
            timerMan.tearDown();

            AsynchInstancesSecondScriptWakeUpManager wakeUpMan = AsynchInstancesSecondScriptWakeUpManager.getInstance();
            wakeUpMan.tearDown();

            PushRetryManager timer = PushRetryManager.getInstance();
            timer.tearDown();

            TimeoutManager timeoutMan = TimeoutManager.getInstance();
            timeoutMan.tearDown();

            serviceManager = null;
            ftpServerManager = null;
            dbgConsole = null;
            internalDatabase = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Level getLogLevel() {
        return Level.toLevel(tbxConfig.getConfigurationValue(ToolboxConfiguration.LOG_LEVEL));
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        try {

            logger.info("Stopping all services");
            if (serviceManager != null) {
                serviceManager.tearDownServices();
            }
            logger.info("Shutting down FTP server");
            if (ftpServerManager != null) {
                ftpServerManager.stopServer();
            }

            logger.info("Shutting down DB");
            if (isDbRunning) {
                try {
                    if (internalDatabase != null) {
                        internalDatabase.close();
                    }
                } catch (SQLException ex) {
                }
            }

            logger.info("Shutting down debug console");
            if (dbgConsole != null) {
                dbgConsole.close();

            }

            TimerManager timerMan = TimerManager.getInstance();
            timerMan.tearDown();

            AsynchInstancesSecondScriptWakeUpManager wakeUpMan = AsynchInstancesSecondScriptWakeUpManager.getInstance();
            wakeUpMan.tearDown();

            PushRetryManager timer = PushRetryManager.getInstance();
            timer.tearDown();

            TimeoutManager timeoutMan = TimeoutManager.getInstance();
            timeoutMan.tearDown();

            serviceManager = null;
            ftpServerManager = null;
            dbgConsole = null;
            internalDatabase = null;
            xmlResPersistence = null;
            logger = null;
        } catch (Exception e) {
        }
    }

    public void contextInitialized(ServletContextEvent servletContextEvent) {
    }

    public static Statement getDbStatement() throws Exception {
        ToolboxInternalDatabase db;

        db = ToolboxInternalDatabase.getInstance();
        return db.getStatement();
    }

    public File getLogFile() {
        return logFile;
    }

    public void setLogFile(File logFile) {
        this.logFile = logFile;
    }

    public void setLogDir(File logDir) {
        this.logDir = logDir;
    }

    public void setRootDir(File root) {
        this.rootDir = root;
    }

    public FTPServerManager getFtpServerManager() {
        return ftpServerManager;
    }

    public void setFtpServerManager(FTPServerManager ftpServerManager) {
        this.ftpServerManager = ftpServerManager;
    }

    public boolean isConfigurationProcessed() {
        return configurationProcessed;
    }

    public void setConfigurationProcessed(boolean configurationProcessed) {
        this.configurationProcessed = configurationProcessed;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public static void setMailError(String aMailError) {
        mailError = aMailError;
    }

    public static String getMailError() {
        return mailError;
    }

    public long getInstanceKeyUnderDebug() {
        return instanceKeyUnderDebug;
    }

    public void setInstanceKeyUnderDebug(long instanceKeyUnderDebug) {
        this.instanceKeyUnderDebug = instanceKeyUnderDebug;
    }

    public ServerDebugConsole getDbgConsole() {
        return dbgConsole;
    }

    public void setDeployAdministrationToken(String token) {
        this.deployAdminToken = token;
    }

    public String getToolboxScriptSchemaLocation() {
        File schemaFile;

        schemaFile = new File(this.rootDir, "WEB-INF/schemas/xmlScript.xsd");
        return schemaFile.getAbsolutePath();
    }

    /**
     * Return true if the service is a secure one, i.e. it has WS-Security policy applied
     * @param req
     * @return
     * @author Stefano
     */
    public boolean isSecureServiceRequest(HttpServletRequest req) {
        String requestURI = req.getRequestURI();
        String serviceName = requestURI.substring(requestURI.lastIndexOf("/") + 1);
        serviceName = requestURI.substring(requestURI.lastIndexOf("/") + 1);
        TBXService service = null;
        try {
            service = serviceManager.getService(serviceName);
            if (service == null) {
                String errorMsg = "Unknown service: " + serviceName;
                logger.error(errorMsg);
                return false;
            }
        } catch (ToolboxException ex) {
            return false;
        }
        return service.isWSSecurity();
    }

    /**
     * Tries to retrieve the requested operation from the given message context.
     * @author Stefano
     * @param msgCtx the Axis2 message context
     * @return the operation name or an empty string
     */
    public static String getOperationName(MessageContext msgCtx) {

        String SOAPnamespaceURI = msgCtx.getEnvelope().getNamespace().getNamespaceURI();

        String operationName = "";
        if (SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI.equals(
                SOAPnamespaceURI)) {
            String soapAction = msgCtx.getSoapAction();

            if (soapAction.startsWith("\"")) {
                soapAction = soapAction.substring(1, soapAction.length() - 1);
            }
            operationName = soapAction;


        } else if (SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI.equals(
                SOAPnamespaceURI)) {
            //TODO fix the operation retrieving in case of SOAP1.2
            String soapAction = msgCtx.getSoapAction();

            if (soapAction != null && soapAction.compareTo("") != 0) {
                return soapAction;
            }

            OMElement bodyFirstChild = msgCtx.getEnvelope().getBody().getFirstElement();
            QName operationQName = new QName(bodyFirstChild.getLocalName());
            operationName = operationQName.getLocalPart();
            if (operationName == null || operationName.compareTo("") == 0) {
                operationName = msgCtx.getWSAAction();
            }

        }
        return operationName;

    }

    protected void initVersionRevision() throws Exception {
        File infoFile;
        Document infoDoc;
        DOMUtil util;
        Element rootEl;

        util = new DOMUtil();

        infoFile = new File(rootDir, "WEB-INF/xml/info.xml");
        infoDoc = util.fileToDocument(infoFile);
        rootEl = infoDoc.getDocumentElement();

        toolboxVersion = rootEl.getAttribute("toolboxVersion");
        toolboxRevision = rootEl.getAttribute("revisionVersion");
    }

    /**
     * @return the toolboxVersion
     */
    public String getToolboxVersion() {
        return toolboxVersion;
    }

    /**
     * @return the toolboxRevision
     */
    public String getToolboxRevision() {
        return toolboxRevision;
    }

    private void executeDeploy(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        DeployServiceCommand sc;

        sc = new DeployServiceCommand();
        sc.executeCommand(req, resp);
    }

    public String getDeployAdminToken() {
        return deployAdminToken;
    }

    public void setDeployAdminToken(String deployAdminToken) {
        this.deployAdminToken = deployAdminToken;
    }

    private void executeRestCommand(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String cmd;
        ManagerPluginManager man;
        IRESTManagerPlugin commandPlugin = null;
        String contentType = null;

        try {
            man = ManagerPluginManager.getInstance();

            cmd = req.getRequestURI();
            cmd = cmd.substring(req.getContextPath().length());

            String formatLessCmd = cmd;
            String lastToken = "";
            StringTokenizer tokenizer = new StringTokenizer(cmd, "/");

            while (tokenizer.hasMoreTokens()) {
                lastToken = tokenizer.nextToken();
            }

            if (lastToken != null) {
                int index = lastToken.lastIndexOf(".");
                if (index > -1) {
                    contentType = lastToken.substring(index + 1);
                    if (contentType.equals("json") == false && contentType.equals("xml") == false) {
                        contentType = null;
                    }

                } else {
                    contentType = null;
                }

            }

            if (contentType != null) {
                formatLessCmd = cmd.substring(0, cmd.length() - contentType.length() - 1);
            }

            String method = req.getMethod();
            if (method.equals("GET")) {
                commandPlugin = (IRESTManagerPlugin) man.getCommand(formatLessCmd, ManagerPluginManager.METHOD_REST_GET);
            } else if (method.equals("POST")) {
                commandPlugin = (IRESTManagerPlugin) man.getCommand(formatLessCmd, ManagerPluginManager.METHOD_REST_POST);
            } else if (method.equals("PUT")) {
                commandPlugin = (IRESTManagerPlugin) man.getCommand(formatLessCmd, ManagerPluginManager.METHOD_REST_PUT);
            } else if (method.equals("DELETE")) {
                commandPlugin = (IRESTManagerPlugin) man.getCommand(formatLessCmd, ManagerPluginManager.METHOD_REST_DELETE);
            } else {
                throw new Exception("Method " + method + " not supported");
            }

            Map<String, String> headers, parameters;

            headers = parseRequestHeaders(req);
            parameters = parseHeaderParameters(req);

            commandPlugin.setInputHeaders(headers);
            commandPlugin.setInputParameters(parameters);

            if (contentType != null && contentType.equals("json")) {
                executeRestCommandJSON(req, resp, commandPlugin, formatLessCmd);
            } else if (contentType != null && contentType.equals("xml")) {
                executeRestCommandXML(req, resp, commandPlugin, formatLessCmd);
            } else {
                try {
                    executeRestCommandStream(req, resp, commandPlugin, formatLessCmd);
                } catch (UnsupportedOperationException ex) {
                    executeRestCommandServletInterface(req, resp, commandPlugin);
                }
            }

            setOutputHeaders(commandPlugin, resp);

            resp.setStatus(resp.SC_OK);
        } catch (Exception e) {
            resp.sendError(resp.SC_BAD_REQUEST);
        }
    }

    private Map<String, String> parseRequestHeaders(HttpServletRequest req) {
        Enumeration<String> en = req.getHeaderNames();

        HashMap<String, String> headers;

        headers = new HashMap<String, String>();
        while (en.hasMoreElements()) {
            String name = en.nextElement();
            headers.put(name, req.getHeader(name));
        }

        return headers;
    }

    private Map<String, String> parseHeaderParameters(HttpServletRequest req) {
        Enumeration<String> en = req.getParameterNames();

        HashMap<String, String> headers;

        headers = new HashMap<String, String>();
        while (en.hasMoreElements()) {
            String name = en.nextElement();
            headers.put(name, req.getParameter(name));
        }

        return headers;
    }
}
