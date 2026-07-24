package gov.iti.telecom;

import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.command.AgiCommand;
import org.asteriskjava.fastagi.command.AnswerCommand;
import org.asteriskjava.fastagi.command.StreamFileCommand;

public class IvrAgiScript {

    public void service(AgiRequest request, AgiChannel channel)
            throws AgiException {

        System.out.println("Incoming AGI request");

        channel.sendCommand(new AnswerCommand());

        channel.sendCommand(
            new StreamFileCommand("hello")
        );
    }
}