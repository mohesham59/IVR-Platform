package gov.iti.telecom.vxml;

import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.BaseAgiScript;

import java.util.HashMap;
import java.util.Map;

/**
 * VxmlInterpreter — Executes a VxmlDocument AST using FastAGI primitives.
 */
public class VxmlInterpreter {

    public static void execute(VxmlDocument doc, BaseAgiScript agi, String callerId) throws AgiException {
        if (doc.getDialogs().isEmpty()) {
            System.err.println("[VxmlInterpreter] VXML document has no dialogs!");
            return;
        }

        Map<String, String> sessionVars = new HashMap<>(doc.getVariables());
        sessionVars.put("caller_id", callerId);

        VxmlDialog currentDialog = doc.getDialogs().get(0);

        while (currentDialog != null) {
            System.out.println("[VxmlInterpreter] [" + callerId + "] Executing VXML dialog: " + currentDialog.getId() + " (" + currentDialog.getType() + ")");

            if (currentDialog instanceof VxmlForm) {
                currentDialog = executeForm((VxmlForm) currentDialog, doc, agi, sessionVars, callerId);
            } else if (currentDialog instanceof VxmlMenu) {
                currentDialog = executeMenu((VxmlMenu) currentDialog, doc, agi, callerId);
            } else {
                break;
            }
        }
    }

    private static VxmlDialog executeForm(VxmlForm form, VxmlDocument doc, BaseAgiScript agi, Map<String, String> sessionVars, String callerId) throws AgiException {
        if (form.getAudioSrc() != null && !form.getAudioSrc().isEmpty()) {
            String audio = stripExtension(form.getAudioSrc());
            agi.streamFile(audio);
        } else if (form.getPrompt() != null && !form.getPrompt().isEmpty()) {
            System.out.println("[VxmlInterpreter] Prompt (TTS): " + form.getPrompt());
        }

        if (form.getFieldName() != null && !form.getFieldName().isEmpty()) {
            String input = agi.getData(form.getAudioSrc() != null ? stripExtension(form.getAudioSrc()) : "silence/1", 10000, form.getFieldLength());
            sessionVars.put(form.getFieldName(), input != null ? input : "");
            System.out.println("[VxmlInterpreter] Field " + form.getFieldName() + " = " + input);
        }

        if (form.getTransferDest() != null && !form.getTransferDest().isEmpty()) {
            System.out.println("[VxmlInterpreter] Transferring call to VXML destination: " + form.getTransferDest());
            agi.exec("Transfer", form.getTransferDest());
        }

        if (form.isDisconnect()) {
            System.out.println("[VxmlInterpreter] VoiceXML <disconnect/> encountered. Terminating dialog execution.");
            return null;
        }

        if (form.getNextTarget() != null && !form.getNextTarget().isEmpty()) {
            return doc.getDialogById(form.getNextTarget());
        }

        return null;
    }

    private static VxmlDialog executeMenu(VxmlMenu menu, VxmlDocument doc, BaseAgiScript agi, String callerId) throws AgiException {
        String promptAudio = menu.getAudioSrc() != null ? stripExtension(menu.getAudioSrc()) : "demo-instruct";

        System.out.println("[VxmlInterpreter] Menu prompt: " + menu.getPrompt());
        String digit = agi.getData(promptAudio, 5000, 1);

        System.out.println("[VxmlInterpreter] Caller DTMF selection: " + digit);

        if (digit != null && !digit.isEmpty()) {
            for (VxmlChoice choice : menu.getChoices()) {
                if (digit.equals(choice.getDtmf())) {
                    System.out.println("[VxmlInterpreter] Matched DTMF choice " + digit + " -> " + choice.getNext());
                    return doc.getDialogById(choice.getNext());
                }
            }
        }

        return null;
    }

    private static String stripExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(0, filename.lastIndexOf('.'));
        }
        return filename;
    }
}
