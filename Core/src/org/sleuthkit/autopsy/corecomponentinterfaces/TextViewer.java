/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
 */
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.Component;
import org.openide.nodes.Node;

/**
 * Interface that TextViewers for the TextContentViewer must implement. These
 * modules analyze an individual file that the user has selected and display
 * results in some form of JPanel.
 *
 * TextViewer panels should handle their own vertical scrolling, the horizontal
 * scrolling when under their panel's preferred size will be handled by the
 * TextContentViewerPanel which contains them.
 */
public interface TextViewer {

    /**
     * Autopsy will call this when this panel is focused with the file that
     * should be analyzed. When called with null, must clear all references to
     * previous nodes.
     *
     * @param selectedNode the node which is used to determine what is displayed
     *                     in this viewer
     */
    public void setNode(Node selectedNode);

    /**
     * Returns the title of this viewer to display in the tab.
     *
     * @return the title of TextViewer
     */
    public String getTitle();

    /**
     * Returns a short description of this viewer to use as a tool tip for its
     * tab.
     *
     * @return the tooltip for this TextViewer
     */
    public String getToolTip();

    /**
     * Create and return a new instance of your viewer. The reason that this is
     * needed is because the specific viewer modules will be found via NetBeans
     * Lookup and the type will only be TextViewer. This method is used to get
     * an instance of your specific type.
     *
     * @return A new instance of the viewer
     */
    public TextViewer createInstance();

    /**
     * Return the Swing Component to display. Implementations of this method
     * that extend JPanel and do a 'return this;'. Otherwise return an internal
     * instance of the JPanel.
     *
     * @return the component which is displayed for this viewer
     */
    public Component getComponent();

    /**
     * Resets the contents of the viewer / component.
     */
    public void resetComponent();

    /**
     * Checks whether the given node is supported by the viewer. This will be
     * used to enable or disable the tab for the viewer.
     *
     * @param node Node to check for support
     *
     * @return True if the node can be displayed / processed, else false
     */
    public boolean isSupported(Node node);

    /**
     * Checks whether the given viewer is preferred for the Node. This is a bit
     * subjective, but the idea is that Autopsy wants to display the most
     * relevant tab. The more generic the viewer, the lower the return value
     * should be. This will only be called on viewers that support the given
     * node (i.e., isSupported() has already returned true).
     *
     * The following are some examples of the current levels in use. If the
     * selected node is an artifact, the level may be determined by both the
     * artifact and its associated file.
     *
     * Level 7 - Based on the artifact, if any, in the selected node and
     * specific to an artifact type or types. The current text viewer that can
     * return level 7 is the Indexed Text tab when the selected node is a
     * Keyword Search hit.
     *
     * Level 6 - Based on the artifact, if any, in the selected node but not
     * restricted to particular types.
     *
     * Level 5 - Based on the file in the selected node and very specific to the
     * file type.
     *
     * Level 4 - Based on the file in the selected node but fairly general.
     * Currently this is the level returned by the Indexed Text tab if Keyword
     * Search has been run (unless the node is a Keyword Search hit or a Credit
     * Card account). This is the default tab for most files.
     *
     * Level 3 - Based on the artifact, if any, in the selected node where the
     * artifact is thought to be of less interest than the associated file.
     *
     * Level 1 - Very general and should always be available. The Strings tabs
     * is this this level
     *
     * Level 0 - For cases where the text viewer should never be displayed by
     * default.
     *
     * @param node Node to check for preference
     *
     * @return an int (0-10) higher return means the viewer has higher priority
     */
    public int isPreferred(Node node);
}
