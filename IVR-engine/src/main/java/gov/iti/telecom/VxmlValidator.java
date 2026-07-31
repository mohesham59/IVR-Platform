package gov.iti.telecom;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * VxmlValidator — validates VXML documents for correctness and completeness.
 *
 * <h2>HOW IT WORKS</h2>
 * <ol>
 *   <li>Checks basic VXML 2.1 structure and namespaces</li>
 *   <li>Verifies required elements (form, menu, block)</li>
 *   <li>Extracts transfer destinations for chaining logic</li>
 *   <li>Collects validation errors for reporting</li>
 *   <li>Provides detailed error messages with context</li>
 * </ol>
 *
 * <h2>USAGE EXAMPLE</h2>
 * <pre>{@code
 * VxmlValidator validator = new VxmlValidator();
 *
 * // Validate a VXML document
 * Document vxmlDoc = loader.loadVxml("hello");
 * ValidationResult result = validator.validate(vxmlDoc);
 *
 * if (result.isValid()) {
 *     System.out.println("VXML is valid");
 * } else {
 *     result.getErrors().forEach(e -> System.err.println(e));
 * }
 *
 * // Extract transfer destinations
 * List<String> destinations = validator.extractTransferDestinations(vxmlDoc);
 * System.out.println("Transfers to: " + destinations);
 * }</pre>
 *
 * <h2>VALIDATION CHECKS</h2>
 * <ul>
 *   <li>Document is not null</li>
 *   <li>Root element is &lt;vxml&gt; with correct namespace</li>
 *   <li>Version attribute is 2.1 or compatible</li>
 *   <li>At least one &lt;form&gt; element exists</li>
 *   <li>Each form has at least one interactive element (block, menu, field, etc.)</li>
 *   <li>Transfer elements have valid destinations</li>
 * </ul>
 *
 * @author IVR Platform Team
 * @version 1.0
 * @see VxmlLoader
 */
public class VxmlValidator {

    private static final String VXML_NAMESPACE = "http://www.w3.org/2001/vxml";
    private List<ValidationError> errors;

    /**
     * Creates a new VXML validator instance.
     */
    public VxmlValidator() {
        this.errors = new ArrayList<>();
    }

    /**
     * Validates a VXML document for correctness and completeness.
     *
     * <p>This method performs multiple validation checks and collects all errors.
     * It does NOT throw exceptions; instead, errors are returned in the
     * ValidationResult object.</p>
     *
     * @param doc the DOM document to validate (typically from VxmlLoader)
     * @return a ValidationResult containing validation status and any errors found
     * @throws NullPointerException if doc is null
     *
     * @see ValidationResult
     * @see #validate(org.w3c.dom.Document)
     */
    public ValidationResult validate(Document doc) {
        if (doc == null) {
            throw new NullPointerException("Document to validate cannot be null");
        }

        errors.clear();

        // Check root element
        Element root = doc.getDocumentElement();
        if (root == null) {
            errors.add(new ValidationError("No root element found"));
            return new ValidationResult(false, errors);
        }

        if (!"vxml".equals(root.getLocalName())) {
            errors.add(new ValidationError("Root element must be <vxml>, found: <" + root.getTagName() + ">"));
        }

        // Check namespace
        if (!VXML_NAMESPACE.equals(root.getNamespaceURI())) {
            errors.add(new ValidationError(
                    "VXML namespace mismatch. Expected: " + VXML_NAMESPACE +
                            ", Found: " + root.getNamespaceURI()));
        }

        // Check version
        String version = root.getAttribute("version");
        if (version == null || version.isEmpty()) {
            errors.add(new ValidationError("Missing required 'version' attribute on <vxml>"));
        } else if (!version.equals("2.1") && !version.startsWith("2.")) {
            errors.add(new ValidationError("VXML version mismatch. Expected 2.1, found: " + version));
        }

        // Check for forms
        NodeList forms = doc.getElementsByTagNameNS(VXML_NAMESPACE, "form");
        if (forms.getLength() == 0) {
            errors.add(new ValidationError("No <form> elements found. At least one form is required."));
        }

        // Validate each form
        for (int i = 0; i < forms.getLength(); i++) {
            Element form = (Element) forms.item(i);
            String formId = form.getAttribute("id");
            if (formId == null || formId.isEmpty()) {
                errors.add(new ValidationError("Form " + i + " missing required 'id' attribute"));
            } else {
                validateForm(form);
            }
        }

        // Check for transfers
        NodeList transfers = doc.getElementsByTagNameNS(VXML_NAMESPACE, "transfer");
        for (int i = 0; i < transfers.getLength(); i++) {
            Element transfer = (Element) transfers.item(i);
            String dest = transfer.getAttribute("dest");
            if (dest == null || dest.isEmpty()) {
                errors.add(new ValidationError("Transfer element " + i + " missing 'dest' attribute"));
            }
        }

        System.out.println("[VxmlValidator] Validation complete. Errors: " + errors.size());
        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Validates the content of a single form element.
     *
     * @param form the form element to validate
     */
    private void validateForm(Element form) {
        String formId = form.getAttribute("id");
        NodeList children = form.getChildNodes();

        // Check if form has interactive content
        boolean hasContent = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element child = (Element) children.item(i);
                String localName = child.getLocalName();
                if ("block".equals(localName) || "menu".equals(localName) ||
                        "field".equals(localName) || "initial".equals(localName)) {
                    hasContent = true;
                    break;
                }
            }
        }

        if (!hasContent) {
            errors.add(new ValidationError(
                    "Form '" + formId + "' has no interactive content (block, menu, field, etc.)"));
        }
    }

    /**
     * Checks if a form with the given ID exists in the document.
     *
     * @param doc the VXML document to search
     * @param formId the ID of the form to find
     * @return true if a form with the given ID exists, false otherwise
     */
    public boolean hasForm(Document doc, String formId) {
        NodeList forms = doc.getElementsByTagNameNS(VXML_NAMESPACE, "form");
        for (int i = 0; i < forms.getLength(); i++) {
            Element form = (Element) forms.item(i);
            if (formId.equals(form.getAttribute("id"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts all transfer destinations from a VXML document.
     *
     * <p>Useful for understanding the VXML flow and planning session chaining.</p>
     *
     * @param doc the VXML document to analyze
     * @return a list of transfer destination URIs (may include file:// or http:// URLs)
     *
     * @example
     * <pre>{@code
     * List<String> destinations = validator.extractTransferDestinations(doc);
     * // Returns: ["file:///scenarios/menu.vxml", "sip:operator@example.com"]
     * }</pre>
     */
    public List<String> extractTransferDestinations(Document doc) {
        List<String> destinations = new ArrayList<>();
        NodeList transfers = doc.getElementsByTagNameNS(VXML_NAMESPACE, "transfer");

        for (int i = 0; i < transfers.getLength(); i++) {
            Element transfer = (Element) transfers.item(i);
            String dest = transfer.getAttribute("dest");
            if (dest != null && !dest.isEmpty()) {
                destinations.add(dest);
            }
        }

        System.out.println("[VxmlValidator] Found " + destinations.size() + " transfer destinations");
        return destinations;
    }

    /**
     * Returns a list of all errors found during the last validation.
     *
     * @return list of ValidationError objects
     */
    public List<ValidationError> getErrors() {
        return new ArrayList<>(errors);
    }

    /**
     * Clears the error list.
     */
    public void clearErrors() {
        errors.clear();
    }

    /**
     * ValidationResult — immutable holder for validation results.
     *
     * @author IVR Platform Team
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = new ArrayList<>(errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<ValidationError> getErrors() {
            return new ArrayList<>(errors);
        }

        public int getErrorCount() {
            return errors.size();
        }

        @Override
        public String toString() {
            return "ValidationResult{" +
                    "valid=" + valid +
                    ", errorCount=" + errors.size() +
                    '}';
        }
    }

    /**
     * ValidationError — represents a single validation error.
     *
     * @author IVR Platform Team
     */
    public static class ValidationError {
        private final String message;

        public ValidationError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "[ValidationError] " + message;
        }
    }
}
