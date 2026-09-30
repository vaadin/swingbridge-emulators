/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.testapps.crud.swing;

import java.util.Date;

public final class Employee {

    public enum Role { ADMIN, USER, GUEST }

    private String name = "";
    private String password = "";
    private String bio = "";
    private boolean active = true;
    private Role role = Role.USER;
    private int level = 1;
    private int rating = 50;
    private boolean favorite = false;
    private Date dateOfBirth = new Date();

    public Employee() { }

    public Employee(Employee other) {
        this.name = other.name;
        this.password = other.password;
        this.bio = other.bio;
        this.active = other.active;
        this.role = other.role;
        this.level = other.level;
        this.rating = other.rating;
        this.favorite = other.favorite;
        this.dateOfBirth = other.dateOfBirth == null ? null : new Date(other.dateOfBirth.getTime());
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public Date getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(Date dateOfBirth) { this.dateOfBirth = dateOfBirth; }
}
