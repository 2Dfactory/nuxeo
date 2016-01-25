/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 * $Id: TemplateWidgetTypeHandler.java 28244 2007-12-18 19:44:57Z atchertchian $
 */

package org.nuxeo.ecm.platform.forms.layout.facelets.plugins;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.faces.component.UIComponent;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.FaceletHandler;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagAttributes;
import javax.faces.view.facelets.TagConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.forms.layout.api.FieldDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.Widget;
import org.nuxeo.ecm.platform.forms.layout.api.exceptions.WidgetException;
import org.nuxeo.ecm.platform.forms.layout.facelets.FaceletHandlerHelper;
import org.nuxeo.ecm.platform.forms.layout.facelets.RenderVariables;
import org.nuxeo.ecm.platform.forms.layout.facelets.ValueExpressionHelper;
import org.nuxeo.ecm.platform.ui.web.binding.BlockingVariableMapper;
import org.nuxeo.ecm.platform.ui.web.binding.MapValueExpression;
import org.nuxeo.ecm.platform.ui.web.binding.MetaValueExpression;
import org.nuxeo.ecm.platform.ui.web.tag.handler.TagConfigFactory;

import com.sun.faces.facelets.tag.ui.DecorateHandler;

/**
 * Template widget type.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class TemplateWidgetTypeHandler extends AbstractWidgetTypeHandler {

    private static final Log log = LogFactory.getLog(TemplateWidgetTypeHandler.class);

    public static final String TEMPLATE_PROPERTY_NAME = "template";

    /**
     * Property that can be put on the widget type definition to decide whether the widget type should bind to parent
     * value when no field is set
     *
     * @since 5.6
     */
    public static final String BIND_VALUE_IF_NO_FIELD_PROPERTY_NAME = "bindValueIfNoField";

    public TemplateWidgetTypeHandler(TagConfig config) {
        super(config);
    }

    @Override
    public void apply(FaceletContext ctx, UIComponent parent, Widget widget) throws WidgetException, IOException {
        String template = getTemplateValue(widget);
        if (template == null) {
            log.error("Missing template property for widget " + widget.getName() + " in layout "
                    + widget.getLayoutName());
            return;
        }
        FaceletHandlerHelper helper = new FaceletHandlerHelper(tagConfig);
        String widgetId = widget.getId();
        TagAttributes attributes = helper.getTagAttributes(widgetId, widget);
        TagAttribute templateAttr = getTemplateAttribute(helper);
        if (templateAttr == null) {
            templateAttr = helper.createAttribute(TEMPLATE_PROPERTY_NAME, template);
        }
        attributes = FaceletHandlerHelper.addTagAttribute(attributes, templateAttr);
        String widgetTagConfigId = widget.getTagConfigId();
        TagConfig config = TagConfigFactory.createTagConfig(tagConfig, widgetTagConfigId, attributes, nextHandler);

        VariableMapper cvm = ctx.getVariableMapper();
        if (!(cvm instanceof BlockingVariableMapper)) {
            throw new IllegalArgumentException(
                    "Current context variable mapper should be an instance of MetaVariableMapper");
        }
        BlockingVariableMapper vm = (BlockingVariableMapper) cvm;
        fillVariablesForRendering(ctx, vm, widget);

        new DecorateHandler(config).apply(ctx, parent);
    }

    /**
     * Computes variables for rendering, making available the field values in templates using the format "field_0",
     * "field_1", etc. and also the widget properties using the format "widgetProperty_thePropertyName".
     */
    protected void fillVariablesForRendering(FaceletContext ctx, BlockingVariableMapper vm, Widget widget) {
        ExpressionFactory eFactory = ctx.getExpressionFactory();

        FieldDefinition[] fieldDefs = widget.getFieldDefinitions();
        // expose field variables
        FieldDefinition firstField = null;
        if (fieldDefs != null && fieldDefs.length > 0) {
            for (int i = 0; i < fieldDefs.length; i++) {
                if (i == 0) {
                    addFieldVariable(ctx, eFactory, vm, widget, fieldDefs[i], null);
                    firstField = fieldDefs[i];
                }
                addFieldVariable(ctx, eFactory, vm, widget, fieldDefs[i], Integer.valueOf(i));
            }
        } else if (getBindValueIfNoFieldValue(widget)) {
            // expose value as first parameter
            addFieldVariable(ctx, eFactory, vm, widget, null, null);
            addFieldVariable(ctx, eFactory, vm, widget, null, Integer.valueOf(0));
        }
        vm.addBlockedPattern(RenderVariables.widgetVariables.field.name() + "*");

        // add binding "fieldOrValue" available since 5.6, in case template
        // widget is always supposed to bind value when no field is defined
        String computedValue = ValueExpressionHelper.createExpressionString(widget.getValueName(), firstField);
        vm.setVariable(RenderVariables.widgetVariables.fieldOrValue.name(),
                eFactory.createValueExpression(ctx, computedValue, Object.class));
        vm.addBlockedPattern(RenderVariables.widgetVariables.fieldOrValue.name());

        // expose widget properties too
        Map<String, ValueExpression> mappedExpressions = new HashMap<String, ValueExpression>();
        for (Map.Entry<String, Serializable> prop : widget.getProperties().entrySet()) {
            String key = prop.getKey();
            String name = RenderVariables.widgetVariables.widgetProperty.name() + "_" + key;
            ValueExpression ve = eFactory.createValueExpression(prop.getValue(), Object.class);
            vm.setVariable(name, new MetaValueExpression(ve, ctx.getFunctionMapper(), vm));
            mappedExpressions.put(key, ve);
        }
        vm.addBlockedPattern(RenderVariables.widgetVariables.widgetProperty.name() + "_*");
        vm.setVariable(RenderVariables.widgetVariables.widgetProperties.name(),
                new MapValueExpression(mappedExpressions));
        vm.addBlockedPattern(RenderVariables.widgetVariables.widgetProperties.name());
        // expose widget controls too
        for (Map.Entry<String, Serializable> ctrl : widget.getControls().entrySet()) {
            String key = ctrl.getKey();
            String name = RenderVariables.widgetVariables.widgetControl.name() + "_" + key;
            vm.setVariable(name, eFactory.createValueExpression(ctrl.getValue(), Object.class));
        }
        vm.addBlockedPattern(RenderVariables.widgetVariables.widgetControl.name() + "_*");
    }

    protected void addFieldVariable(FaceletContext ctx, ExpressionFactory eFactory, BlockingVariableMapper vm,
            Widget widget, FieldDefinition fieldDef, Integer index) {
        String computedName;
        if (index == null) {
            computedName = RenderVariables.widgetVariables.field.name();
        } else {
            computedName = RenderVariables.widgetVariables.field.name() + "_" + index;
        }
        String computedValue = ValueExpressionHelper.createExpressionString(widget.getValueName(), fieldDef);
        vm.setVariable(computedName, eFactory.createValueExpression(ctx, computedValue, Object.class));
    }

    /**
     * Returns the "template" property value, looking up on the widget definition definition first, and on the widget
     * type definition if not found.
     */
    protected String getTemplateValue(Widget widget) {
        return lookupProperty(TEMPLATE_PROPERTY_NAME, widget);
    }

    /**
     * Helper method to retrieve a property value, looking up on the widget definition first, and on the widget type
     * definition if not found.
     *
     * @since 7.2
     */
    protected String lookupProperty(String name, Widget widget) {
        // lookup in the widget configuration
        String val = (String) widget.getProperty(name);
        if (val == null) {
            // lookup in the widget type configuration
            val = getProperty(name);
        }
        return val;
    }

    /**
     * Returns the "bindValueIfNoField" property value, looking up on the widget type definition first, and on the
     * widget definition if not found.
     *
     * @since 5.6
     * @param widget
     * @return
     */
    protected boolean getBindValueIfNoFieldValue(Widget widget) {
        Object value = getProperty(BIND_VALUE_IF_NO_FIELD_PROPERTY_NAME);
        if (value == null) {
            value = widget.getProperty(BIND_VALUE_IF_NO_FIELD_PROPERTY_NAME);
        }
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value);
        }
        return Boolean.TRUE.equals(Boolean.valueOf(value.toString()));

    }

    /**
     * Returns the template attribute.
     */
    protected TagAttribute getTemplateAttribute(FaceletHandlerHelper helper) {
        // do not return anything as it will be computed from the widget
        // properties anyway.
        return null;
    }

}
