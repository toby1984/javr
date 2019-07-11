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
