package org.sa.rainbow.models.ports.eseb;

import org.sa.rainbow.management.ports.eseb.ESEBConstants;
import org.sa.rainbow.management.ports.eseb.RainbowESEBMessage;
import org.sa.rainbow.models.commands.IRainbowModelCommandRepresentation;

public class ESEBCommandHelper implements ESEBConstants {

    public static void command2Message (IRainbowModelCommandRepresentation command, RainbowESEBMessage msg) {
        msg.setProperty (MODEL_NAME_KEY, command.getModelName ());
        msg.setProperty (COMMAND_NAME_KEY, command.getCommandName ());
        msg.setProperty (MODEL_TYPE_KEY, command.getModelType ());
        msg.setProperty (COMMAND_TARGET_KEY, command.getTarget ());
        for (int i = 0; i < command.getParameters ().length; i++) {
            msg.setProperty (COMMAND_PARAMETER_KEY + i, command.getParameters ()[i]);
        }
    }

}
