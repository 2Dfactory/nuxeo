package org.nuxeo.opensocial.container.shared.webcontent;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author Stéphane Fourrier
 */
public interface WebContentData extends Serializable {
    public boolean isCollapsed();

    public void setIsCollapsed(boolean isCollapsed);

    public boolean isInAPorlet();

    public void setIsInAPortlet(boolean isInAPortlet);

    public String getId();

    public void setId(String id);

    public String getName();

    public void setName(String name);

    public String getTitle();

    public void setTitle(String title);

    public String getUnitId();

    public void setUnitId(String unitId);

    public long getPosition();

    public void setPosition(long position);

    public void addPreference(String pref, String value);

    public void setPreferences(Map<String, String> preferences);

    public Map<String, String> getPreferences();

    public void setOwner(String owner);

    public String getOwner();

    public void setViewer(String viewer);

    public String getViewer();

    public boolean initPrefs(Map<String, String> params);

    public void updateFrom(WebContentData data);

    public String getAssociatedType();

    public String getIcon();

    public boolean hasFiles();

    public void addFile(Serializable file);

    public void clearFiles();

    public List<Serializable> getFiles();
}
