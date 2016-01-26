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
 * $Id: LayoutRowWidgetTagHandler.java 30553 2008-02-24 15:51:31Z atchertchian $
 */

package org.nuxeo.ecm.platform.forms.layout.facelets;

import java.io.IOException;

import javax.el.ELException;
import javax.el.VariableMapper;
import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.view.facelets.FaceletContext;
import javax.faces.view.facelets.FaceletException;
import javax.faces.view.facelets.TagAttribute;
import javax.faces.view.facelets.TagConfig;
import javax.faces.view.facelets.TagHandler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.forms.layout.api.LayoutRow;
import org.nuxeo.ecm.platform.forms.layout.api.Widget;
import org.nuxeo.ecm.platform.ui.web.binding.BlockingVariableMapper;

/**
 * Layout widget recursion tag handler.
 * <p>
 * Iterates over a layout row widgets and apply next handlers as many times as needed.
 * <p>
 * Only works when used inside a tag using the {@link LayoutRowTagHandler}.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class LayoutRowWidgetTagHandler extends TagHandler {

    private static final Log log = LogFactory.getLog(LayoutRowWidgetTagHandler.class);

    protected final TagConfig config;

    /**
     * @since 7.2
     */
    protected final TagAttribute recomputeIds;

    public LayoutRowWidgetTagHandler(TagConfig config) {
        super(config);
        this.config = config;
        recomputeIds = getAttribute("recomputeIds");
    }

    /**
     * For each widget in current row, exposes widget variables and applies next handler.
     * <p>
     * Needs row to be exposed in context, so works in conjunction with {@link LayoutRowTagHandler}.
     * <p>
     * Widget variables exposed: {@link RenderVariables.widgetVariables#widget} , same variable suffixed with "_n" where
     * n is the widget level, and {@link RenderVariables.widgetVariables#widgetIndex}.
     */
    public void apply(FaceletContext ctx, UIComponent parent)
            throws IOException, FacesException, FaceletException, ELException {

        // resolve widgets from row in context
        LayoutRow row = null;
        String rowVariableName = getInstanceName();
        FaceletHandlerHelper helper = new FaceletHandlerHelper(config);
        TagAttribute rowAttribute = helper.createAttribute(rowVariableName, "#{" + rowVariableName + "}");
        if (rowAttribute != null) {
            row = (LayoutRow) rowAttribute.getObject(ctx, LayoutRow.class);
        }
        if (row == null) {
            log.error("Could not resolve layout row " + rowAttribute);
            return;
        }

        Widget[] widgets = row.getWidgets();
        if (widgets == null || widgets.length == 0) {
            return;
        }

        boolean recomputeIdsBool = false;
        if (recomputeIds != null) {
            recomputeIdsBool = recomputeIds.getBoolean(ctx);
        }

        VariableMapper orig = ctx.getVariableMapper();
        try {
            int widgetCounter = 0;
            for (Widget widget : widgets) {
                BlockingVariableMapper vm = new BlockingVariableMapper(orig);
                ctx.setVariableMapper(vm);

                // set unique id on widget before exposing it to the context, but assumes iteration could be done
                // several times => do not generate id again if already set, unless specified by attribute
                // "recomputeIds"
                if (widget != null && (widget.getId() == null || recomputeIdsBool)) {
                    WidgetTagHandler.generateWidgetId(ctx, helper, widget, false);
                }

                WidgetTagHandler.exposeWidgetVariables(ctx, vm, widget, widgetCounter, true);

                nextHandler.apply(ctx, parent);
                widgetCounter++;
            }
        } finally {
            ctx.setVariableMapper(orig);
        }
    }

    protected String getInstanceName() {
        return RenderVariables.rowVariables.layoutRow.name();
    }

}
