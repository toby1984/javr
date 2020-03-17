/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.ui;

import java.util.Optional;

public class GlobalSettings
{
    private String nameOfDefaultProjectToOpen;

    public GlobalSettings() {
    }

    public GlobalSettings(GlobalSettings other) {
        this.nameOfDefaultProjectToOpen = other.nameOfDefaultProjectToOpen;
    }

    public GlobalSettings createCopy() {
        return new GlobalSettings(this);
    }

    /**
     * Returns the project name (if any) of the project to open when starting the application.
     *
     * If no project name is set and more than one project is available in the workspace,
     * a popup will ask the user which project to open.
     *
     * @return
     */
    public Optional<String> getDefaultProjectToOpen() {
        return Optional.ofNullable( nameOfDefaultProjectToOpen );
    }

    /**
     * Sets the project name (if any) of the project to open when starting the application.
     *
     * If no project name is set and more than one project is available in the workspace,
     * a popup will ask the user which project to open.
     *
     * @param projectName project name or NULL to ask the user on startup
     */
    public void setDefaultProjectToOpen(String projectName)
    {
        this.nameOfDefaultProjectToOpen = projectName;
    }
}
