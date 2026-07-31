package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VxmlForm {
    private String id;
    private final List<VxmlPrompt> prompts = new ArrayList<>();
    private VxmlBlock block;
    private final List<VxmlField> fields = new ArrayList<>();
    private VxmlMenu menu;
    private VxmlTransfer transfer;
    private boolean disconnect;
    private final List<VxmlIf> ifs = new ArrayList<>();

    public VxmlForm(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<VxmlPrompt> getPrompts() {
        return prompts;
    }

    public void addPrompt(VxmlPrompt prompt) {
        if (prompt != null) prompts.add(prompt);
    }

    public VxmlBlock getBlock() {
        return block;
    }

    public void setBlock(VxmlBlock block) {
        this.block = block;
    }

    public List<VxmlField> getFields() {
        return fields;
    }

    public void addField(VxmlField field) {
        if (field != null) fields.add(field);
    }

    public VxmlMenu getMenu() {
        return menu;
    }

    public void setMenu(VxmlMenu menu) {
        this.menu = menu;
    }

    public VxmlTransfer getTransfer() {
        return transfer;
    }

    public void setTransfer(VxmlTransfer transfer) {
        this.transfer = transfer;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public List<VxmlIf> getIfs() {
        return ifs;
    }

    public void addIf(VxmlIf ifObj) {
        if (ifObj != null) ifs.add(ifObj);
    }

    public String getPrimaryPromptText() {
        if (block != null && block.getPrimaryPromptText() != null) {
            return block.getPrimaryPromptText();
        }
        if (!prompts.isEmpty()) {
            return prompts.get(0).getText();
        }
        return null;
    }

    public String getGotoTarget() {
        if (block != null && block.getGoto() != null) {
            String target = block.getGoto().getNext();
            if (target != null && target.startsWith("#")) {
                return target.substring(1);
            }
            return target;
        }
        return null;
    }

    public List<String> getAllTransitions() {
        List<String> targets = new ArrayList<>();
        if (block != null) {
            collectTransitions(block, targets);
        }
        for (VxmlField field : fields) {
            if (field.getFilled() != null) {
                collectTransitions(field.getFilled(), targets);
            }
            if (field.getNoInput() != null) {
                collectTransitions(field.getNoInput(), targets);
            }
            if (field.getNoMatch() != null) {
                collectTransitions(field.getNoMatch(), targets);
            }
        }
        if (menu != null) {
            for (VxmlChoice choice : menu.getChoices()) {
                String next = choice.getNext();
                if (next != null) {
                    if (next.startsWith("#")) {
                        targets.add(next.substring(1));
                    } else {
                        targets.add(next);
                    }
                }
            }
            VxmlGoto elseGoto = menu.getElseGoto();
            if (elseGoto != null && elseGoto.getNext() != null) {
                String target = elseGoto.getNext();
                if (target.startsWith("#")) {
                    targets.add(target.substring(1));
                } else {
                    targets.add(target);
                }
            }
        }
        if (transfer != null) {
            targets.add(transfer.getDest());
        }
        for (VxmlIf ifObj : ifs) {
            collectTransitions(ifObj, targets);
        }
        return targets;
    }

    private void collectTransitions(VxmlBlock block, List<String> targets) {
        if (block == null) return;
        if (block.getGoto() != null) {
            String target = block.getGoto().getNext();
            if (target != null) {
                if (target.startsWith("#")) {
                    targets.add(target.substring(1));
                } else {
                    targets.add(target);
                }
            }
        }
        if (block.getIf() != null) {
            collectTransitions(block.getIf(), targets);
        }
    }

    private void collectTransitions(VxmlIf ifObj, List<String> targets) {
        if (ifObj == null) return;
        if (ifObj.getGoto() != null) {
            String target = ifObj.getGoto().getNext();
            if (target != null) {
                if (target.startsWith("#")) {
                    targets.add(target.substring(1));
                } else {
                    targets.add(target);
                }
            }
        }
        if (ifObj.getElseBranch() != null) {
            collectTransitions(ifObj.getElseBranch(), targets);
        }
        for (VxmlIf elseIf : ifObj.getElseIfs()) {
            collectTransitions(elseIf, targets);
        }
    }
}
