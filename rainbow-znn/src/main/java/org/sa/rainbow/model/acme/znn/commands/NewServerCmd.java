/*
 * The MIT License
 *
 * Copyright 2014 CMU ABLE Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.sa.rainbow.model.acme.znn.commands;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.acmestudio.acme.ModelHelper;
import org.acmestudio.acme.element.IAcmeComponent;
import org.acmestudio.acme.model.IAcmeCommandFactory;
import org.acmestudio.acme.model.command.IAcmeAttachmentCommand;
import org.acmestudio.acme.model.command.IAcmeCommand;
import org.acmestudio.acme.model.command.IAcmeComponentCreateCommand;
import org.acmestudio.acme.model.command.IAcmeConnectorCreateCommand;
import org.acmestudio.acme.model.command.IAcmePortCreateCommand;
import org.acmestudio.acme.model.command.IAcmePropertyCommand;
import org.acmestudio.acme.model.command.IAcmePropertyCreateCommand;
import org.acmestudio.acme.model.util.core.UMStringValue;
import org.sa.rainbow.core.error.RainbowModelException;
import org.sa.rainbow.model.acme.AcmeModelInstance;

/**
 * Creates a new server in Znn and attaches it to the indicated load balancer
 * 
 * @author Bradley Schmerl: schmerl
 *
 */
public class NewServerCmd extends ZNNAcmeModelCommand<IAcmeComponent> {

    private static final List<String>   SERVER_TYPE        = Arrays.asList ("ServerT");

    private static final List<String>   HTTP_CONN_T        = Arrays.asList ("ProxyConnT");

    private static final List<String>   PROXY_FORWARD_PORT = Arrays.asList ("ProxyForwardPortT");

    private static final List<String>   HTTP_PORT          = Arrays.asList ("HttpPortT");

    private String                      m_name;
    public String                       m_lb;

    private IAcmeComponentCreateCommand m_serverCommand;

    private String                      m_host;

    private String                      m_port;

    /**
     * 
     * @param commandName
     *            The name for the command
     * @param model
     *            The model in which the new server will be created
     * @param lb
     *            The name of the load balancer to which the server will be attached
     * @param name
     *            The name to give the new server
     * @param host
     *            The host on which the server is running
     * @param port
     *            The port on which the server will accept HTTP requests
     */
    public NewServerCmd (String commandName, AcmeModelInstance model, String lb, String name, String host, String port) {
        super (commandName, model, lb, name, host, port);
        m_lb = lb;
        m_name = name;
        m_host = host;
        m_port = port;
    }


    /**
     * Executes the list of commands to execute the following operations
     * 
     * <pre>
     * {@code
     *   ServerT name = new ServerT ();
     *   HttpTPortT http = new HttpPortT () in name;
     *   ProxyConnT proxyconn = new ProxyConnT ();
     *   ProxyForwardPortT fwd = new ProxyForwardPortT () in lb;
     *   attach lb.fwd to proxyconn.req;
     *   attach http to proxyconn.rec; // maybe name.http?
     *   name.deploymentLocation = host;
     *   name.httpPort = port;
     * }
     * </pre>
     */
    @Override
    protected List<IAcmeCommand<?>> doConstructCommand () throws RainbowModelException {

        IAcmeComponent lb = getModelContext ().resolveInModel (m_lb, IAcmeComponent.class);

        IAcmeCommandFactory cf = getModel ().getCommandFactory ();
        // Component <m_name> = new ServerT extended with {
        m_name = ModelHelper.getUniqueName (getModel (), m_name);
        m_serverCommand = cf.componentCreateCommand (getModel (), m_name,
                SERVER_TYPE, SERVER_TYPE);
        //   port http : HttpPortT = new HttpPortT;
        // }
        final IAcmePortCreateCommand httpCreateCommand = cf.portCreateCommand (m_serverCommand, "http", HTTP_PORT,
                HTTP_PORT);
        String lbName = ModelHelper.getUniqueName (getModel (), "proxyconn");
        // Connector proxyconn : ProxyConnT = new ProxyConnT;
        final IAcmeConnectorCreateCommand proxyConnCreateCmd = cf.connectorCreateCommand (getModel (), lbName,
                HTTP_CONN_T, HTTP_CONN_T);
        String fwd = ModelHelper.getUniqueName (lb, "fwd");
        // m_lb = m_lb extended with {
        //   port fwd : ProxyForwardPortT = new ProxyForwardPortT;
        // }
        final IAcmePortCreateCommand lbPortCmd = cf.portCreateCommand (lb, fwd, PROXY_FORWARD_PORT,
                PROXY_FORWARD_PORT);
        // attachment m_lb.fwd to proxyconn.req;
        final IAcmeAttachmentCommand attachLBEnd = cf.attachmentCreateCommand (getModel (),
                lb.getName () + "." + fwd,
                lbName + ".req");
        // attachment <m_name>.http to proxyconn.rec;
        final IAcmeAttachmentCommand attachServerEnd = cf.attachmentCreateCommand (getModel (), m_name + ".http",
                lbName + ".rec");

        final IAcmePropertyCommand setLocation = cf.propertyCreateCommand (m_serverCommand, "deploymentLocation",
                "String",
                new UMStringValue (m_host));
        final IAcmePropertyCreateCommand setPort = cf.propertyCreateCommand (m_serverCommand, "httpPort", "String",
                new UMStringValue (m_port));
        List<IAcmeCommand<?>> cmds = new LinkedList<> ();
        cmds.add (m_serverCommand);
        cmds.add (httpCreateCommand);
        cmds.add (proxyConnCreateCmd);
        cmds.add (lbPortCmd);
        cmds.add (attachLBEnd);
        cmds.add (attachServerEnd);
        cmds.add (setLocation);
        cmds.add (setPort);

        return cmds;
    }


    @Override
    public IAcmeComponent getResult () {
        return m_serverCommand.getComponent ();
    }

    @Override
    public String getName () {
        return "newServer";
    }


}
