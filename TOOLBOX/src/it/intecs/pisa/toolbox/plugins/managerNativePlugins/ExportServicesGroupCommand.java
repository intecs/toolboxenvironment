
package it.intecs.pisa.toolbox.plugins.managerNativePlugins;

import it.intecs.pisa.pluginscore.exceptions.GenericException;
import it.intecs.pisa.toolbox.constants.ServiceConstants;
import it.intecs.pisa.util.DOMUtil;
import it.intecs.pisa.util.IOUtil;
import it.intecs.pisa.util.SchemaSetRelocator;
import it.intecs.pisa.util.ServiceFoldersFilter;
import it.intecs.pisa.util.Zip;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author Andrea Marongiu
 */
public class ExportServicesGroupCommand extends NativeCommandsManagerPlugin{

    private static String SERVICES_PARM ="services";
    private static String ZIP_FILE_PREFIX ="TOOLBOX_SERVICES_";
    protected static final String ZIP_DEPLOY_PACKAGE_MIME_TYPE = "application/x-zip-compressed";


    public void executeCommand(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String servicesList=req.getParameter(SERVICES_PARM);
        String [] services=servicesList.split(",");
        int i;
        File tempServiceDir,tempGroupServiceDir;
        File tempDir;
        String serviceName="";
        File serviceDir;
        File tempSchemaDir;
        File sourceSchemaDIr;
        File zipPackage,zipPackageServiceGroup;
        OutputStream out;
        File descriptorFile;
        File securityResourceFolder;
        Document descriptor;
        Element rootDesc;
        String security;
        DOMUtil util= new DOMUtil();

        try {
            tempDir = new File(System.getProperty("java.io.tmpdir"), "exportPackages");
                if (tempDir.exists() == false) {
                    tempDir.mkdir();
                }
            zipPackageServiceGroup = new File(tempDir, ZIP_FILE_PREFIX+servicesList.hashCode() + ".zip");
            tempGroupServiceDir = new File(tempDir, ZIP_FILE_PREFIX+servicesList.hashCode());
                if (tempGroupServiceDir.exists()) {
                    IOUtil.rmdir(tempGroupServiceDir);
                }
            tempGroupServiceDir.mkdir();

            for(i=0; i<services.length; i++){
                serviceName=services[i];

                tempServiceDir = new File(tempDir, serviceName);
                if (tempServiceDir.exists()) {
                    IOUtil.rmdir(tempServiceDir);
                }
                tempServiceDir.mkdir();

                serviceDir = tbxServlet.getServiceRoot(serviceName);

                IOUtil.copyDirectory(serviceDir, tempServiceDir);

                /*Check Security*/
                descriptorFile = new File(tempServiceDir, ServiceConstants.SERVICE_DESCRIPTOR_FILE_NAME);
                descriptor = util.fileToDocument(descriptorFile);
                rootDesc = descriptor.getDocumentElement();
                security = rootDesc.getAttribute("wssecurity");
                if (security.equals("true")) {
                    /*Remove Security*/
                    rootDesc.setAttribute("wssecurity", "false");
                    DOMUtil.dumpXML(descriptor, descriptorFile);
                    securityResourceFolder= new File(tempServiceDir, ServiceConstants.SERVICE_SERVICE_RESOURCE_FOLDER);
                    if(securityResourceFolder.exists())
                       IOUtil.rmdir(securityResourceFolder);
                }

                tempSchemaDir = new File(tempServiceDir, "Schemas");
                sourceSchemaDIr = new File(serviceDir, "Schemas");

                SchemaSetRelocator.updateSchemaLocationToRelative(tempSchemaDir, sourceSchemaDIr.toURI());

                zipPackage = new File(tempDir, serviceName + ".zip");
                String [] fileFilters= { "Schemas" , "Info", "Resources", "AdditionalResources", "serviceDescriptor.xml", "Operations"};
                
                ServiceFoldersFilter filter= new ServiceFoldersFilter(fileFilters);
                Zip.zipDirectory(zipPackage.getAbsolutePath(), tempServiceDir.getAbsolutePath(), false, filter);
                IOUtil.copy(new FileInputStream(zipPackage), new FileOutputStream(new File(tempGroupServiceDir, serviceName + ".zip")));
            }

            resp.setContentType(ZIP_DEPLOY_PACKAGE_MIME_TYPE);
            resp.setHeader("Content-disposition", "attachment; filename=" + zipPackageServiceGroup.getName());

            Zip.zipDirectory(zipPackageServiceGroup.getAbsolutePath(), tempGroupServiceDir.getAbsolutePath(), false);


            out=resp.getOutputStream();
         //   if(zipPackageServiceGroup.getTotalSpace() < MiscConstants.MAX_READ_BYTES)
            IOUtil.copy(new FileInputStream(zipPackageServiceGroup), out);
          //  else
            //  out.write("The Export zip file is too large.".getBytes());
            out.flush();
            out.close();

        } catch (Exception ex) {
            String errorMsg = "Error getting service descriptor ("+serviceName+"): " + CDATA_S + ex.getMessage() + CDATA_E;
             throw new GenericException(errorMsg);
        }

    }

}
