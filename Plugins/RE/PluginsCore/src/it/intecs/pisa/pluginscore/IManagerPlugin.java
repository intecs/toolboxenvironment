/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package it.intecs.pisa.pluginscore;

import java.io.File;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Massimiliano Fanciulli
 */
public interface IManagerPlugin {
    public void executeCommand(HttpServletRequest req, HttpServletResponse resp) throws Exception;
    public void setPluginDirectory(File dir) throws Exception;
    public boolean validateInput(HttpServletRequest req, HttpServletResponse resp);
    public boolean authenticate(HttpServletRequest req, HttpServletResponse resp);
}
