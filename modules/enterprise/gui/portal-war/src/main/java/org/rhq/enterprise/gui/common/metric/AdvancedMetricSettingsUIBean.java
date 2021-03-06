/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.common.metric;

import java.util.Calendar;
import java.util.Date;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import org.rhq.core.gui.util.FacesContextUtility;
import org.rhq.enterprise.gui.legacy.WebUser;
import org.rhq.enterprise.gui.util.EnterpriseFacesContextUtility;
import org.rhq.enterprise.server.measurement.MeasurementPreferences;
import org.rhq.enterprise.server.measurement.MeasurementPreferences.MetricRangePreferences;

public class AdvancedMetricSettingsUIBean {

    private static final String DURATION_TYPE = "duration";
    private static final String INTERVAL_TYPE = "interval";

    private int duration;
    private Integer unit;
    private String intervalType;
    private String durationType;
    private Date fromTime;
    private Date toTime;

    public AdvancedMetricSettingsUIBean() {
        init();
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public Integer getUnit() {
        return unit;
    }

    public void setUnit(Integer unit) {
        this.unit = unit;
    }

    public void setIntervalType(String intervalType) {
        this.intervalType = intervalType;
    }

    public String getIntervalType() {
        return this.intervalType;
    }

    public void setDurationType(String durationType) {
        this.durationType = durationType;
    }

    public String getDurationType() {
        return this.durationType;
    }

    public Date getFromTime() {
        return fromTime;
    }

    public void setFromTime(Date fromTime) {
        this.fromTime = fromTime;
    }

    public Date getToTime() {
        return toTime;
    }

    public void setToTime(Date toTime) {
        this.toTime = toTime;
    }

    public void execute() {
        FacesContext facesContext = FacesContextUtility.getFacesContext();
        WebUser user = EnterpriseFacesContextUtility.getWebUser();
        MeasurementPreferences preferences = user.getMeasurementPreferences();
        MetricRangePreferences rangePreferences = preferences.getMetricRangePreferences();

        String metricType = "";
        if (this.getIntervalType() == null) {
            metricType = getDurationType();
        } else {
            metricType = getIntervalType();
        }
        if ((metricType == null) || (metricType.equals(""))) {
            facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Range select error",
                "Please select one option to either setup the duration or the time interval"));
        } else if (metricType.equalsIgnoreCase(AdvancedMetricSettingsUIBean.DURATION_TYPE)) {
            rangePreferences.readOnly = false;
            rangePreferences.lastN = duration;
            rangePreferences.unit = unit;
            facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Preferences updated",
                "Your preferences have been successfully updated"));
        } else if (metricType.equalsIgnoreCase(AdvancedMetricSettingsUIBean.INTERVAL_TYPE)) {
            if ((this.getFromTime() == null) || (this.getToTime() == null)) {
                facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Range select error",
                    "Please make sure to fill in the from and to dates"));
            } else {
                Long fromTime = this.getFromTime().getTime();
                Long toTime = this.getToTime().getTime();
                Long now = Calendar.getInstance().getTime().getTime();
                if ((toTime == null) || (fromTime == null)) {
                    facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Timing error",
                        "Please fill in the required fields"));
                } else if (toTime < fromTime) {
                    facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Timing error",
                        "To time cannot be earlier than before time"));
                } else if (toTime > now || fromTime > now) {
                    facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Timing error",
                        "Cannot set time interval in the future"));
                } else {
                    rangePreferences.readOnly = true;
                    rangePreferences.begin = fromTime;
                    rangePreferences.end = toTime;
                }
                facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Preferences updated",
                    "Your preferences have been successfully updated"));
            }
        }
        preferences.setMetricRangePreferences(rangePreferences);
    }

    public void init() {
        WebUser user = EnterpriseFacesContextUtility.getWebUser();
        MeasurementPreferences preferences = user.getMeasurementPreferences();
        MetricRangePreferences rangePreferences = preferences.getMetricRangePreferences();
        if (rangePreferences.readOnly) {
            this.setDurationType(null);
            this.setIntervalType("interval");
            this.setUnit(null);
            this.setFromTime(new Date(rangePreferences.begin));
            this.setToTime(new Date(rangePreferences.end));
        } else {
            this.setDurationType("duration");
            this.setIntervalType(null);
            this.setDuration(rangePreferences.lastN);
            this.setUnit(rangePreferences.unit);
            this.setFromTime(null);
            this.setToTime(null);
        }
    }
}
