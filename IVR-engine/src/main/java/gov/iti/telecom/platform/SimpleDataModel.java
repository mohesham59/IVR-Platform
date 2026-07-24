package gov.iti.telecom.platform;

import java.util.HashMap;
import java.util.Map;

import org.jvoicexml.event.error.SemanticError;
import org.jvoicexml.interpreter.datamodel.DataModel;
import org.jvoicexml.interpreter.datamodel.DataModelObjectSerializer;
import org.jvoicexml.interpreter.scope.Scope;

public class SimpleDataModel implements DataModel {

    private final Map<String, Object> variables = new HashMap<>();

    @Override
    public DataModel newInstance() {
        return new SimpleDataModel();
    }

    @Override
    public Object getUndefinedValue() {
        return null;
    }

    @Override
    public Object createNewObject() {
        return new Object();
    }

    @Override
    public String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    @Override
    public int createScope() {
        return 0;
    }

    @Override
    public int createScope(Scope scope) {
        return 0;
    }

    @Override
    public int deleteScope() {
        return -1;
    }

    @Override
    public int deleteScope(Scope scope) {
        return -1;
    }

    @Override
    public int createVariable(String name) {
        return createVariable(name, null);
    }

    @Override
    public int createVariableFor(Object obj, String name) {
        return createVariable(name);
    }

    @Override
    public int createVariable(String name, Object value) {
        variables.put(name, value);
        return 0;
    }

    @Override
    public int createVariableFor(Object obj, String name, Object value) {
        return createVariable(name, value);
    }

    @Override
    public int createVariable(String name, Object value, Scope scope) {
        return createVariable(name, value);
    }

    @Override
    public int createArray(String name, int size) {
        return 0;
    }

    @Override
    public int createArray(String name, int size, Scope scope) {
        return 0;
    }

    @Override
    public int resizeArray(String name, int size) {
        return -1;
    }

    @Override
    public int resizeArray(String name, int size, Scope scope) {
        return -1;
    }

    @Override
    public boolean existsVariable(String name) {
        return variables.containsKey(name);
    }

    @Override
    public boolean existsVariable(String name, Scope scope) {
        return existsVariable(name);
    }

    @Override
    public int deleteVariable(String name) {
        variables.remove(name);
        return 0;
    }

    @Override
    public int deleteVariable(String name, Scope scope) {
        return deleteVariable(name);
    }

    @Override
    public int updateVariable(String name, Object value) {
        return createVariable(name, value);
    }

    @Override
    public int updateVariableFor(Object obj, String name, Object value) {
        return updateVariable(name, value);
    }

    @Override
    public int updateVariable(String name, Object value, Scope scope) {
        return updateVariable(name, value);
    }

    @Override
    public int updateArray(String name, int index, Object value) {
        return -1;
    }

    @Override
    public int updateArray(String name, int index, Object value, Scope scope) {
        return -1;
    }

    @Override
    public <T> T readVariable(String name, Class<T> type) throws SemanticError {
        @SuppressWarnings("unchecked")
        T value = (T) variables.get(name);
        return value;
    }

    @Override
    public <T> T readVariable(String name, Scope scope, Class<T> type) throws SemanticError {
        return readVariable(name, type);
    }

    @Override
    public <T> T readArray(String name, int index, Class<T> type) throws SemanticError {
        return null;
    }

    @Override
    public <T> T readArray(String name, int index, Scope scope, Class<T> type) throws SemanticError {
        return null;
    }

    @Override
    public <T> T evaluateExpression(String expression, Class<T> type) throws SemanticError {
        return null;
    }

    @Override
    public <T> T evaluateExpression(String expression, Scope scope, Class<T> type) throws SemanticError {
        return null;
    }

    @Override
    public DataModelObjectSerializer getSerializer() {
        return null;
    }
}
