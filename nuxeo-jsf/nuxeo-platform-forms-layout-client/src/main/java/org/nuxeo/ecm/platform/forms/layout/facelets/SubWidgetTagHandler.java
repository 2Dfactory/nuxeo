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
 * $Id: SubWidgetTagHandler.java 30553 2008-02-24 15:51:31Z atchertchian $
 */

package org.nuxeo.ecm.platform.forms.layout.facelets;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import javax.el.ELException;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagConfig;
import javax.faces.view.facelets.TagHandler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.forms.layout.api.Widget;
import org.nuxeo.ecm.platform.ui.web.binding.MetaValueExpression;
import org.nuxeo.ecm.platform.ui.web.binding.MetaVariableMapper;

/**
 * SubWidget tag handler.
 * <p>
 * Iterates over a widget subwidgets and apply next handlers as many times as needed.
 * <p>
 * Only works when used inside a tag using the {@link WidgetTagHandler}.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class SubWidgetTagHandler extends TagHandler {

    private static final Log log = LogFactory.getLog(SubWidgetTagHandler.class);

    protected final TagConfig config;

    /**
     * @since 7.2
     */
    protected final TagAttribute recomputeIds;

    public SubWidgetTagHandler(TagConfig config) {
        super(config);
        this.config = config;
        recomputeIds = getAttribute("recomputeIds");
    }

    /**
     * For each subwidget in current widget, exposes widget variables and applies next handler.
     * <p>
     * Needs widget to be exposed in context, so works in conjunction with {@link WidgetTagHandler}.
     * <p>
     * Widget variables exposed: {@link RenderVariables.widgetVariables#widget} , same variable suffixed with "_n" where
     * n is the widget level, and {@link RenderVariables.widgetVariables#widgetIndex}.
     */
    public void apply(FaceletContext ctx, UIComponent parent) throws IOException, FacesException, ELException {
        // resolve subwidgets from widget in context
        Widget widget = null;
        String widgetVariableName = RenderVariables.widgetVariables.widget.name();
        FaceletHandlerHelper helper = new FaceletHandlerHelper(config);
        TagAttribute widgetAttribute = helper.createAttribute(widgetVariableName, "#{" + widgetVariableName + "}");
        if (widgetAttribute != null) {
            widget = (Widget) widgetAttribute.getObject(ctx, Widget.class);
        }
        if (widget == null) {
            log.error("Could not resolve widget " + widgetAttribute);
            return;
        }

        Widget[] subWidgets = widget.getSubWidgets();
        if (subWidgets == null || subWidgets.length == 0) {
            return;
        }

        boolean recomputeIdsBool = false;
        if (recomputeIds != null) {
            recomputeIdsBool = recomputeIds.getBoolean(ctx);
        }

        VariableMapper orig = ctx.getVariableMapper();
        try {
            int subWidgetCounter = 0;
            for (Widget subWidget : subWidgets) {
                MetaVariableMapper vm = new MetaVariableMapper(orig);
                ctx.setVariableMapper(vm);

                // set unique id on widget before exposing it to the context, but assumes iteration could be done
                // several times => do not generate id again if already set, unless specified by attribute
                // "recomputeIds"
                if (subWidget != null && (subWidget.getId() == null || recomputeIdsBool)) {
                    WidgetTagHandler.generateWidgetId(ctx, helper, subWidget, false);
                }

                // expose widget variables
                ExpressionFactory eFactory = ctx.getExpressionFactory();
                ValueExpression subWidgetVe = eFactory.createValueExpression(subWidget, Widget.class);
                Integer level = null;
                if (subWidget != null) {
                    level = Integer.valueOf(subWidget.getLevel());
                }
                vm.setVariable(RenderVariables.widgetVariables.widget.name(), subWidgetVe);
                ValueExpression subWidgetIndexVe = eFactory.createValueExpression(Integer.valueOf(subWidgetCounter),
                        Integer.class);
                vm.addBlockedPattern(RenderVariables.widgetVariables.widget.name());
                vm.setVariable(RenderVariables.widgetVariables.widgetIndex.name(), subWidgetIndexVe);
                vm.setVariable(RenderVariables.widgetVariables.widgetIndex.name() + "_" + level, subWidgetIndexVe);
                vm.addBlockedPattern(RenderVariables.widgetVariables.widgetIndex.name() + "*");

                // expose widget controls too
                if (widget != null) {
                    for (Map.Entry<String, Serializable> ctrl : widget.getControls().entrySet()) {
                        String key = ctrl.getKey();
                        String name = RenderVariables.widgetVariables.widgetControl.name() + "_" + key;
                        String value = "#{" + RenderVariables.widgetVariables.widget.name() + ".controls." + key + "}";
                        ValueExpression ve = eFactory.createValueExpression(ctx, value, Object.class);
                        vm.setVariable(name, new MetaValueExpression(ve, ctx.getFunctionMapper(), vm));
                    }
                    vm.addBlockedPattern(RenderVariables.widgetVariables.widgetControl.name() + "_*");
                }

                nextHandler.apply(ctx, parent);
                subWidgetCounter++;
            }
        } finally {
            ctx.setVariableMapper(orig);
        }

    }
}
