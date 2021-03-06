package com.inductiveautomation.opcua.sdk.core.model.variables;

import com.inductiveautomation.opcua.sdk.core.model.UaMandatory;
import com.inductiveautomation.opcua.stack.core.types.builtin.unsigned.UInteger;

public interface SamplingIntervalDiagnosticsType extends BaseDataVariableType {

    @UaMandatory("SamplingInterval")
    Double getSamplingInterval();

    @UaMandatory("SampledMonitoredItemsCount")
    UInteger getSampledMonitoredItemsCount();

    @UaMandatory("MaxSampledMonitoredItemsCount")
    UInteger getMaxSampledMonitoredItemsCount();

    @UaMandatory("DisabledMonitoredItemsSamplingCount")
    UInteger getDisabledMonitoredItemsSamplingCount();

    void setSamplingInterval(Double samplingInterval);

    void setSampledMonitoredItemsCount(UInteger sampledMonitoredItemsCount);

    void setMaxSampledMonitoredItemsCount(UInteger maxSampledMonitoredItemsCount);

    void setDisabledMonitoredItemsSamplingCount(UInteger disabledMonitoredItemsSamplingCount);

}
