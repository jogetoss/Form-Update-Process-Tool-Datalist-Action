package org.joget.marketplace;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.datalist.model.DataListPluginExtend;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.ApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class FormUpdateProcessToolDatalistAction extends DataListActionDefault implements DataListPluginExtend{
    private final static String MESSAGE_PATH = "messages/FormUpdateProcessToolDatalistAction";
 
    @Override
    public String getName() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.FormUpdateProcessToolDatalistAction.name", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.4";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }
    
    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.FormUpdateProcessToolDatalistAction.name", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.FormUpdateProcessToolDatalistAction.desc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion, appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/FormUpdateProcessToolDatalistAction.json", arguments, true, MESSAGE_PATH);
        
        return json;
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-tools\"></i>";
    }

    @Override
    public String getLinkLabel() {
        return getPropertyString("label"); //get label from configured properties options
    }

    @Override
    public String getHref() {
        return getPropertyString("href"); //Let system to handle to post to the same page
    }
    
    @Override
    public String getTarget() {
        return "post";
    }
    
    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");  //Let system to set the parameter to the checkbox name
    }

    @Override
    public String getHrefColumn() {
        return getPropertyString("hrefColumn"); //Let system to set the primary key column of the binder
    }

    @Override
    public String getConfirmation() {
        return getPropertyString("confirmation"); //get confirmation from configured properties options
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        
        DataListActionResult result = new DataListActionResult();
        result.setType(DataListActionResult.TYPE_REDIRECT);
        result.setUrl("REFERER");
        
        // only allow POST to cater to form submission
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            return result;
        }
        
        // check for submited rows
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        boolean debugMode = Boolean.parseBoolean((String)getProperty("debug"));
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        String formDataJson = WorkflowUtil.getHttpServletRequest().getParameter("bulkcompleteformdata");
        Form form = getForm(getPropertyString("popupFormId"));
        int delay = Integer.parseInt((String)getProperty("delay"));
        Object objProcessTool = getProperty("processTool");
        
        //iterate thru rowKeys
        int recordCount = 0;
        if (rowKeys != null && rowKeys.length > 0) {
            for(String recordIdObj : rowKeys){
                recordCount++;
                String recordId = recordIdObj;
                
                if(debugMode){
                    LogUtil.info(getClass().getName(), "Iterating item " + recordCount + " - Record " + recordId);
                }

                if (recordId != null && !recordId.isEmpty()) {
                    //save form data - only works when datalist action is in bulk action
                    if (form != null && form.getStoreBinder() != null && formDataJson != null) {
                        String formDataRecordId = appService.getOriginProcessId(recordId);
                        FormData formData = getFormData(formDataJson, formDataRecordId, null, form);
                        if (formData != null) {
                            formService.recursiveExecuteFormStoreBinders(form, form, formData);
                        }
                    }
                    
                    if (objProcessTool != null && objProcessTool instanceof Map) {
                        Map fvMap = (Map) objProcessTool;
                        if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                            String className = fvMap.get("className").toString();
                            ApplicationPlugin p = (ApplicationPlugin)pluginManager.getPlugin(className);
                            Map propertiesMap = (Map) fvMap.get("properties");

                            //create mock assignment
                            WorkflowAssignment wfAssignment = null;
                            wfAssignment = new WorkflowAssignment();
                            wfAssignment.setProcessId(recordId);

                            //obtain plugin defaults
                            propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) p, (Map) fvMap.get("properties"), appDef, wfAssignment));

                            //replace recordID inside the plugin's properties
                            Map propertiesMapWithRecordID = replaceValueHashMap(propertiesMap, recordId, wfAssignment);

                            if(debugMode){
                                LogUtil.info(getClass().getName(), "Executing tool: " + className);
                            }

                            ApplicationPlugin appPlugin = (ApplicationPlugin) p;

                            propertiesMapWithRecordID.put("workflowAssignment", wfAssignment);
                            propertiesMapWithRecordID.put("appDef", appDef);
                            propertiesMapWithRecordID.put("pluginManager", pluginManager);

                            if(request != null){
                                propertiesMapWithRecordID.put("request", request);
                            }

                            if (appPlugin instanceof PropertyEditable) {
                                ((PropertyEditable) appPlugin).setProperties(propertiesMapWithRecordID);
                            }

                            Object resultPlugin = appPlugin.execute(propertiesMapWithRecordID);

                            if(debugMode){
                                if(resultPlugin != null){
                                    LogUtil.info(getClass().getName(), "Executed tool: " + className + " - " + resultPlugin.toString());
                                }else{
                                    LogUtil.info(getClass().getName(), "Executed tool: " + className);
                                }
                            }
                        }
                    }

                    if(delay > 0){
                        try {
                            Thread.sleep(delay * 1000);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(FormUpdateProcessToolDatalistAction.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }

                if(debugMode){
                    LogUtil.info(getClass().getName(), "Finished item " + recordCount + " - Record: " + recordId);
                }
            }
        }
        
        //call datalist action
        Object objDatalistAction = getProperty("additionalDataListAction");
        if (objDatalistAction != null && objDatalistAction instanceof Map) {
            Map fvMap = (Map) objDatalistAction;
            if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                String className = fvMap.get("className").toString();
                DataListActionDefault p = (DataListActionDefault)pluginManager.getPlugin(className);
                DataListActionDefault datalistActionPlugin = (DataListActionDefault) p;
                Map datalistActionPropertiesMap = (Map)fvMap.get("properties");
                
                Map parentPropertiesMap = this.getProperties();
                
                //merge with current datalist action properties
                parentPropertiesMap.putAll(datalistActionPropertiesMap);
                
                datalistActionPlugin.setProperties(parentPropertiesMap);
                return datalistActionPlugin.executeAction(dataList, rowKeys);
            }
        }
        
        return result;
    }
    
    @Override
    public String getHTML(DataList dataList) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Map dataModel = new HashMap();
        dataModel.put("datalist", dataList);
        dataModel.put("element", this);
        dataModel.put("htmlScript", getPropertyString("htmlScript"));
        dataModel.put("contextPath", WorkflowUtil.getHttpServletRequest().getContextPath());
        
        String rightToLeft = WorkflowUtil.getSystemSetupValue("rightToLeft");
        String locale = AppUtil.getAppLocale();
        
        dataModel.put("isRtl", Boolean.toString("true".equalsIgnoreCase(rightToLeft) || (locale != null && locale.startsWith("ar"))));
        
        Form form = FormUpdateProcessToolDatalistAction.getForm(getPropertyString("popupFormId"));
        if (form != null) {
            dataModel.put("buttonLabel", StringUtil.escapeString(ResourceBundleUtil.getMessage("form.button.submit"), StringUtil.TYPE_HTML, null));
            String elJson = SecurityUtil.encrypt(StringUtil.escapeString(getSelectedFormJson(form), StringUtil.TYPE_HTML, null));
            dataModel.put("json", elJson);
                                
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            dataModel.put("appDef", AppUtil.getCurrentAppDefinition());
            
            String nonceForm = SecurityUtil.generateNonce(new String[]{"EmbedForm", appDef.getAppId(), appDef.getVersion().toString(), elJson}, 1);
            dataModel.put("nonceForm", nonceForm);
            
            return pluginManager.getPluginFreeMarkerTemplate(dataModel, "org.joget.marketplace.FormUpdateProcessToolDatalistAction", "/templates/FormUpdateProcessToolDatalistActionWithForm.ftl", null);
        } else {
            return pluginManager.getPluginFreeMarkerTemplate(dataModel, "org.joget.marketplace.FormUpdateProcessToolDatalistAction", "/templates/FormUpdateProcessToolDatalistAction.ftl", null);
        }
    }
    
    protected String getSelectedFormJson(Form form) {
        if (form != null) {
            FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
            String json = formService.generateElementJson(form);
            
            //replace the binder in json for popup form
            try {
                JSONObject temp = new JSONObject(json);
                JSONObject jsonProps = temp.getJSONObject(FormUtil.PROPERTY_PROPERTIES);
                
                JSONObject jsonLoadBinder = new JSONObject();
                jsonLoadBinder.put(FormUtil.PROPERTY_CLASS_NAME, "org.joget.plugin.enterprise.JsonFormBinder");
                jsonLoadBinder.put(FormUtil.PROPERTY_PROPERTIES, new JSONObject());
                jsonProps.put(FormBinder.FORM_LOAD_BINDER, jsonLoadBinder);
                jsonProps.put(FormBinder.FORM_STORE_BINDER, jsonLoadBinder);
                
                json = temp.toString();
            } catch (Exception e) {
                //ignore
            }
            
            return SecurityUtil.encrypt(json);
        }
        setProperty(FormUtil.PROPERTY_READONLY, "true");
        
        return "";
    }
    
    protected FormData getFormData(String json, String recordId, String processId, Form form) {
        try {
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(recordId);
            formData.setProcessId(processId);

            FormRowSet rows = new FormRowSet();
            FormRow row = new FormRow();
            rows.add(row);

            JSONObject jsonObject = new JSONObject(json);
            for(Iterator iterator = jsonObject.keys(); iterator.hasNext();) {
                String key = (String) iterator.next();
                if (FormUtil.PROPERTY_TEMP_REQUEST_PARAMS.equals(key)) {
                    JSONObject tempRequestParamMap = jsonObject.getJSONObject(FormUtil.PROPERTY_TEMP_REQUEST_PARAMS);
                    JSONArray tempRequestParams = tempRequestParamMap.names();
                    if (tempRequestParams != null && tempRequestParams.length() > 0) {
                        for (int l = 0; l < tempRequestParams.length(); l++) {                        
                            List<String> rpValues = new ArrayList<String>();
                            String rpKey = tempRequestParams.getString(l);
                            JSONArray tempValues = tempRequestParamMap.getJSONArray(rpKey);
                            if (tempValues != null && tempValues.length() > 0) {
                                for (int m = 0; m < tempValues.length(); m++) {
                                    rpValues.add(tempValues.getString(m));
                                }
                            }
                            formData.addRequestParameterValues(rpKey, rpValues.toArray(new String[]{}));
                        }
                    }
                } else if (FormUtil.PROPERTY_TEMP_FILE_PATH.equals(key)) {
                    JSONObject tempFileMap = jsonObject.getJSONObject(FormUtil.PROPERTY_TEMP_FILE_PATH);
                    JSONArray tempFiles = tempFileMap.names();
                    if (tempFiles != null && tempFiles.length() > 0) {
                        for (int l = 0; l < tempFiles.length(); l++) {                        
                            List<String> rpValues = new ArrayList<String>();
                            String rpKey = tempFiles.getString(l);
                            JSONArray tempValues = tempFileMap.getJSONArray(rpKey);
                            if (tempValues != null && tempValues.length() > 0) {
                                for (int m = 0; m < tempValues.length(); m++) {
                                    String path = tempValues.getString(m);
                                    File file = FileManager.getFileByPath(path);
                                    if (file != null & file.exists()) {
                                        String newPath = UuidGenerator.getInstance().getUuid() + File.separator + file.getName();
                                        FileUtils.copyFile(file, new File(FileManager.getBaseDirectory(), newPath));
                                        rpValues.add(newPath);
                                    }
                                }
                            }
                            row.putTempFilePath(rpKey, rpValues.toArray(new String[]{}));
                        }
                    }
                } else {
                    String value = jsonObject.getString(key);
                    row.setProperty(key, value);
                }
            }
            row.setId(recordId);
            formData.setStoreBinderData(form.getStoreBinder(), rows);
            
            return formData;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, recordId);
            return null;
        }
    }
    
    protected void clearFiles(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            if (jsonObject.has(FormUtil.PROPERTY_TEMP_FILE_PATH)) {
                JSONObject tempFileMap = jsonObject.getJSONObject(FormUtil.PROPERTY_TEMP_FILE_PATH);
                JSONArray tempFiles = tempFileMap.names();
                if (tempFiles != null && tempFiles.length() > 0) {
                    for (int l = 0; l < tempFiles.length(); l++) {                        
                        List<String> rpValues = new ArrayList<String>();
                        String rpKey = tempFiles.getString(l);
                        JSONArray tempValues = tempFileMap.getJSONArray(rpKey);
                        if (tempValues != null && tempValues.length() > 0) {
                            for (int m = 0; m < tempValues.length(); m++) {
                                String path = tempValues.getString(m);
                                FileManager.deleteFileByPath(path);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
        }
    }
    
    public static Form getForm(String formDefId) {
        Form form = null;
        if (formDefId != null && !formDefId.isEmpty()) {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            if (appDef != null) {
                FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
                FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
                FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);

                if (formDef != null) {
                    String json = formDef.getJson();
                    form = (Form) formService.createElementFromJson(json);
                }
            }
        }
        return form;
    }
        
    private static Map replaceValueHashMap(Map map, String recordId, WorkflowAssignment assignment){
        Iterator it = map.entrySet().iterator();
        Map returnMap = new HashMap();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            
            if(pair.getValue() instanceof String){
                String replacedValue = (String)pair.getValue();
                replacedValue = replacedValue.replaceAll("\\$\\$", "#");
                replacedValue = replacedValue.replaceAll("@recordId@", recordId);
                                
                replacedValue = AppUtil.processHashVariable(replacedValue, assignment, (String)null, (Map)null);
                returnMap.put(pair.getKey(), replacedValue);
            }else if(pair.getValue() instanceof Object[]){
                Object[] objects = (Object[])pair.getValue();
                Object[] newObjects = new Object[objects.length];

                int i = 0;
                for(Object obj : objects){
                    Map temp = (Map) obj;
                    temp = replaceValueHashMap(temp, recordId, assignment);
                    newObjects[i] = temp;
                    i++;
                }
                
                returnMap.put(pair.getKey(), newObjects);
            }else if(pair.getValue() instanceof HashMap){
                returnMap.put(pair.getKey(), replaceValueHashMap((HashMap)pair.getValue(), recordId, assignment));
            }else{
                returnMap.put(pair.getKey(), pair.getValue());
            }
        }
        return returnMap;
    }
}
