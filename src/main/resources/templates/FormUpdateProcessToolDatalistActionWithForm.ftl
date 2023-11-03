${htmlScript}

<div id="${element.properties.id}_div" class="bulk_complete_div" style="display:none;">
    <input type="hidden" disabled="disabled" id="formUrl" value="${contextPath}/web/app/${appDef.appId!}/${appDef.version!}/form/embed?_submitButtonLabel=${buttonLabel!?html}">
    <input type="hidden" disabled="disabled" id="json" value="${json!}">
    <input type="hidden" disabled="disabled" id="contextPath" value="${contextPath}">
    <input type="hidden" disabled="disabled" id="nonce" value="${nonceForm!?html}">
</div>
<script>
    function bulk_complete_assignment_${element.properties.id}(args) {
        JPopup.hide("bulkCompleteForm");
        
        var button = $('button[value="${element.properties.id}"]');
        button.after('<div style="display:none;"><textarea id="bulkcompleteformdata" name="bulkcompleteformdata"></textarea><input type="hidden" name="${datalist.actionParamName}" value="${element.properties.id}" /></div>');
        $("#bulkcompleteformdata").val(args.result);
        $(button).closest("form").submit();
    }
    $(document).ready(function() {
        $(document).ready(function() {
            $('button[value="${element.properties.id}"]').off("click");
            $('button[value="${element.properties.id}"]').on("click", function(e){
            
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();

                var button = $(this);
                var table = $(button).closest("form").find('table');

                if ($(table).find("input[type=checkbox][name|=d]:checked, input[type=radio][name|=d]:checked").length > 0) {
                    var params = {
                        _json : $("#${element.properties.id}_div").find("#json").val(),
                        _callback : "bulk_complete_assignment_${element.properties.id}",
                        _setting : "{}",
                        _nonce : $("#${element.properties.id}_div").find("#nonce").val()
                    };

                    JPopup.show("bulkCompleteForm", $("#${element.properties.id}_div").find("#formUrl").val(), params, "", "90%", "90%");
                } else {
                    alert("@@dbuilder.alert.noRecordSelected@@");
                    return false;
                }
            });
        });
    });
</script>
