/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.openwebnet.handler;

import static org.eclipse.smarthome.core.library.unit.SIUnits.CELSIUS;
import static org.openhab.binding.openwebnet.OpenWebNetBindingConstants.*;

import java.math.BigDecimal;
import java.util.Set;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.Thermoregulation;
import org.openwebnet.message.Thermoregulation.LOCAL_OFFSET;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetThermoregulationHandler} is responsible for handling commands/messages for a Thermoregulation
 * OpenWebNet
 * device.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetThermoregulationHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetThermoregulationHandler.class);

    private enum Mode {
        // TODO make it a single map and integrate it with Thermoregulation.WHAT to have automatic translation
        UNKNOWN("UNKNOWN"),
        AUTO("AUTO"),
        MANUAL("MANUAL"),
        PROTECTION("PROTECTION"),
        OFF("OFF");

        private final String mode;

        Mode(final String mode) {
            this.mode = mode;
        }

        @Override
        public String toString() {
            return mode;
        }
    }

    private enum ThermoFunction {
        UNKNOWN(-1),
        COOL(0),
        HEAT(1),
        GENERIC(3);

        private final int function;

        ThermoFunction(final int f) {
            this.function = f;
        }

    }

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.THERMOREGULATION_SUPPORTED_THING_TYPES;

    private boolean isCentralUnit = false;
    private Mode currentSetMode = Mode.UNKNOWN;
    private Mode currentActiveMode = Mode.UNKNOWN;
    private ThermoFunction thermoFunction = ThermoFunction.UNKNOWN;
    private Thermoregulation.LOCAL_OFFSET localOffset = Thermoregulation.LOCAL_OFFSET.NORMAL;

    public OpenWebNetThermoregulationHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:ThermoHandler== constructor");
        if (OpenWebNetBindingConstants.THING_TYPE_BUS_THERMO_CENTRAL_UNIT.equals(thing.getThingTypeUID())) {
            isCentralUnit = true;
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:ThermoHandler== initialize() thing={}", thing.getUID());
    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:ThermoHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        bridgeHandler.gateway.send(Thermoregulation.requestStatus(toWhere(channel)));
    }

    @Override
    protected void handleChannelCommand(ChannelUID channelUID, Command command) {
        switch (channelUID.getId()) {
            case CHANNEL_ALL_TEMP_SETPOINT:
            case CHANNEL_TEMP_SETPOINT:
                handleSetpointCommand(command);
                break;
            case CHANNEL_ALL_SET_MODE:
            case CHANNEL_SET_MODE:
                handleModeCommand(command);
                break;
            default: {
                logger.warn("==OWN:ThermoHandler== Unsupported ChannelUID {}", channelUID);
            }
        }
        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    private void handleSetpointCommand(Command command) {
        logger.debug("==OWN:ThermoHandler== handleSetpointCommand() (command={})", command);
        Unit<Temperature> unit = CELSIUS;
        if (command instanceof QuantityType) {
            QuantityType<Temperature> quantity = commandToQuantityType(command, unit);
            BigDecimal value = quantity.toBigDecimal();
            bridgeHandler.gateway.send(Thermoregulation.requestWriteSetpoint(toWhere(""), value.intValue()));
            // TODO change to device where variable
        } else if (command instanceof DecimalType) {
            BigDecimal value = ((DecimalType) command).toBigDecimal();
            bridgeHandler.gateway.send(Thermoregulation.requestWriteSetpoint(toWhere(""), value.intValue()));
            // TODO change to device where variable
            updateState(CHANNEL_TEMP_SETPOINT, (DecimalType) command);
        } else {
            logger.warn("==OWN:ThermoHandler== Cannot handle command {} for thing {}", command, getThing().getUID());
            return;
        }
    }

    private void handleModeCommand(Command command) {
        logger.debug("==OWN:ThermoHandler== handleModeCommand() (command={})", command);
        if (command instanceof StringType) {
            Mode mode = Mode.valueOf(((StringType) command).toString());
            Thermoregulation.WHAT modeWhat = modeToWhat(mode);
            logger.debug("==OWN:ThermoHandler== handleModeCommand() modeWhat={}", modeWhat);
            if (modeWhat != null) {
                bridgeHandler.gateway.send(Thermoregulation.requestSetMode("#" + toWhere(""), modeWhat));
                // TODO change to device where
            } else {
                logger.warn("==OWN:ThermoHandler== Cannot handle command {} for thing {}", command,
                        getThing().getUID());
            }
        } else {
            logger.warn("==OWN:ThermoHandler== Cannot handle command {} for thing {}", command, getThing().getUID());
        }
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        super.handleMessage(msg);
        if (msg.isCommand()) {
            updateMode((Thermoregulation) msg);
        } else {
            switch (msg.getDim()) {
                case Thermoregulation.DIM_TEMPERATURE:
                case Thermoregulation.DIM_PROBE_TEMPERATURE:
                    updateTemperature((Thermoregulation) msg);
                    break;
                case Thermoregulation.DIM_TEMP_SETPOINT:
                    updateSetpoint((Thermoregulation) msg);
                    break;
                case Thermoregulation.DIM_OFFSET:
                    updateLocalMode((Thermoregulation) msg);
                    break;
                case Thermoregulation.DIM_ACTUATOR_STATUS:
                    updateActuatorStatus((Thermoregulation) msg);
                    break;
                case Thermoregulation.DIM_TEMP_TARGET:
                    updateTargetTemp((Thermoregulation) msg);
                    break;
                default:
                    logger.debug(
                            "==OWN:ThermoHandler== handleMessage() Ignoring unsupported DIM for thing {}. Frame={}",
                            getThing().getUID(), msg);
                    break;
            }
        }
    }

    private void updateMode(Thermoregulation tmsg) {
        logger.debug("==OWN:ThermoHandler== updateMode() for thing: {}", thing.getUID());
        Thermoregulation.WHAT w = (Thermoregulation.WHAT) tmsg.getWhat();
        Mode newMode = whatToMode(w);
        if (newMode != null) {
            if (tmsg.isFromCentralUnit()) {
                updateSetMode(newMode);
            } else {
                updateActiveMode(newMode);
            }
        }
        updateThermoFunction(w);
        updateHeatingCoolingMode();
    }

    private void updateThermoFunction(Thermoregulation.WHAT what) {
        logger.debug("==OWN:ThermoHandler== updateThermoFunction() for thing: {}", thing.getUID());
        ThermoFunction newFunction = null;
        switch (what) {
            case CONDITIONING:
            case PROGRAM_CONDITIONING:
            case MANUAL_CONDITIONING:
            case PROTECTION_CONDITIONING:
            case OFF_CONDITIONING:
            case HOLIDAY_CONDITIONING:
                newFunction = ThermoFunction.COOL;
                break;
            case HEATING:
            case PROGRAM_HEATING:
            case MANUAL_HEATING:
            case PROTECTION_HEATING:
            case OFF_HEATING:
            case HOLIDAY_HEATING:
                newFunction = ThermoFunction.HEAT;
                break;
            case GENERIC:
            case PROGRAM_GENERIC:
            case MANUAL_GENERIC:
            case PROTECTION_GENERIC:
            case OFF_GENERIC:
            case HOLIDAY_GENERIC:
                newFunction = ThermoFunction.GENERIC;
                break;
        }
        if (thermoFunction != newFunction) {
            thermoFunction = newFunction;
            updateState(CHANNEL_THERMO_FUNCTION, new StringType(thermoFunction.toString()));
        }
    }

    private void updateHeatingCoolingMode() {
        logger.debug("==OWN:ThermoHandler== updateHeatingCoolingMode() for thing: {}", thing.getUID());
        if (currentActiveMode == Mode.OFF) {
            updateState(CHANNEL_HEATING_COOLING_MODE, new StringType("off"));
        } else {
            switch (thermoFunction) {
                case HEAT:
                    updateState(CHANNEL_HEATING_COOLING_MODE, new StringType("heat"));
                    break;
                case COOL:
                    updateState(CHANNEL_HEATING_COOLING_MODE, new StringType("cool"));
                    break;
                case GENERIC:
                    updateState(CHANNEL_HEATING_COOLING_MODE, new StringType("heatcool"));
                    break;
                case UNKNOWN:
                default:
                    updateState(CHANNEL_HEATING_COOLING_MODE, UnDefType.NULL);
                    break;
            }
        }
    }

    private void updateSetMode(Mode mode) {
        logger.debug("==OWN:ThermoHandler== updateSetMode() for thing: {}", thing.getUID());
        if (currentSetMode != mode) {
            currentSetMode = mode;
            String channelID;
            if (isCentralUnit) {
                channelID = CHANNEL_ALL_SET_MODE;
            } else {
                channelID = CHANNEL_SET_MODE;
            }
            updateState(channelID, new StringType(currentSetMode.toString()));
        }
    }

    private void updateActiveMode(Mode mode) {
        logger.debug("==OWN:ThermoHandler== updateActiveMode() for thing: {}", thing.getUID());
        if (currentActiveMode != mode) {
            currentActiveMode = mode;
            updateState(CHANNEL_ACTIVE_MODE, new StringType(currentActiveMode.toString()));
        }
    }

    private void updateTemperature(Thermoregulation tmsg) {
        logger.debug("==OWN:ThermoHandler== updateTemperature() for thing: {}", thing.getUID());
        Double temp;
        try {
            temp = Thermoregulation.parseTemperature(tmsg);
            updateState(CHANNEL_TEMPERATURE, new DecimalType(temp));
        } catch (NumberFormatException e) {
            logger.warn("==OWN:ThermoHandler== NumberFormatException on frame {}: {}", tmsg, e);
            updateState(CHANNEL_TEMPERATURE, UnDefType.NULL);
        }
    }

    private void updateSetpoint(Thermoregulation tmsg) {
        logger.debug("==OWN:ThermoHandler== updateSetpoint() for thing: {}", thing.getUID());
        String channelID;
        if (isCentralUnit) {
            channelID = CHANNEL_ALL_TEMP_SETPOINT;
        } else {
            channelID = CHANNEL_TEMP_SETPOINT;
        }
        Double temp;
        try {
            temp = Thermoregulation.parseTemperature(tmsg);
            updateState(channelID, new DecimalType(temp));
        } catch (NumberFormatException e) {
            logger.warn("==OWN:ThermoHandler== updateSetpoint() NumberFormatException on frame {}: {}", tmsg,
                    e.getMessage());
            updateState(channelID, UnDefType.NULL);
        }
    }

    private void updateLocalMode(Thermoregulation msg) {
        logger.debug("==OWN:ThermoHandler== updateLocalMode() for thing: {}", thing.getUID());
        LOCAL_OFFSET newOffset = msg.getLocalOffset();
        if (newOffset != null) {
            localOffset = newOffset;
            logger.debug("==OWN:ThermoHandler== updateLocalMode() new localMode={}", localOffset);
            updateState(CHANNEL_LOCAL_MODE, new StringType(localOffset.getLabel()));
            /*
             * if (localOffset == Thermoregulation.LOCAL_OFFSET.OFF
             * || localOffset == Thermoregulation.LOCAL_OFFSET.PROTECTION) {
             * if (localOffset == Thermoregulation.LOCAL_OFFSET.OFF) {
             * currentActiveMode = Mode.OFF;
             * } else {
             * currentActiveMode = Mode.PROTECTION;
             * }
             * updateState(CHANNEL_ACTIVE_MODE, new StringType(currentActiveMode.toString()));
             * } else {
             * currentActiveMode = currentSetMode;
             * updateState(CHANNEL_ACTIVE_MODE, new StringType(currentActiveMode.toString()));
             * }
             */
        } else {
            logger.warn("==OWN:ThermoHandler== updateLocalMode() unrecognized local offset: {}", msg);
        }
    }

    // TODO Review with more logs
    // TODO use constants
    private void updateActuatorStatus(Thermoregulation msg) {
        logger.debug("==OWN:ThermoHandler== updateActuatorStatus() for thing: {}", thing.getUID());
        int actuator = msg.getActuator();
        if (actuator == 1) {
            updateState(CHANNEL_HEATING, (msg.getActuatorStatus(actuator) == 1 ? OnOffType.ON : OnOffType.OFF));
        } else if (actuator == 2) {
            updateState(CHANNEL_COOLING, (msg.getActuatorStatus(actuator) == 1 ? OnOffType.ON : OnOffType.OFF));
        }
    }

    private void updateTargetTemp(Thermoregulation msg) {
        logger.debug("==OWN:ThermoHandler== updateTargetTemp() for thing: {}", thing.getUID());
        Double temp;
        try {
            temp = Thermoregulation.parseTemperature(msg);
            updateState(CHANNEL_TEMP_TARGET, new DecimalType(temp));
        } catch (NumberFormatException e) {
            logger.warn("==OWN:ThermoHandler== NumberFormatException on frame {}: {}", msg, e);
            updateState(CHANNEL_TEMP_TARGET, UnDefType.NULL);
        }
    }

    private static Mode whatToMode(Thermoregulation.WHAT w) {
        Mode m = null;
        switch (w) {
            case PROGRAM_HEATING:
            case PROGRAM_CONDITIONING:
            case PROGRAM_GENERIC:
                m = Mode.AUTO;
                break;
            case MANUAL_HEATING:
            case MANUAL_CONDITIONING:
            case MANUAL_GENERIC:
                m = Mode.MANUAL;
                break;
            case PROTECTION_HEATING:
            case PROTECTION_CONDITIONING:
            case PROTECTION_GENERIC:
                m = Mode.PROTECTION;
                break;
            case OFF_HEATING:
            case OFF_CONDITIONING:
            case OFF_GENERIC:
                m = Mode.OFF;
                break;
            case CONDITIONING:
                break;
            case GENERIC:
                break;
            case HEATING:
                break;
            case HOLIDAY_CONDITIONING:
            case HOLIDAY_GENERIC:
            case HOLIDAY_HEATING:
            default:
                break;
        }
        return m;
    }

    private Thermoregulation.WHAT modeToWhat(Mode m) {
        Thermoregulation.WHAT newWhat = null;
        switch (m) {
            case AUTO:
                if (thermoFunction == ThermoFunction.GENERIC) {
                    newWhat = Thermoregulation.WHAT.PROGRAM_GENERIC;
                } else if (thermoFunction == ThermoFunction.COOL) {
                    newWhat = Thermoregulation.WHAT.PROGRAM_CONDITIONING;
                } else {
                    newWhat = Thermoregulation.WHAT.PROGRAM_HEATING;
                }
                break;
            case MANUAL:
                if (thermoFunction == ThermoFunction.GENERIC) {
                    newWhat = Thermoregulation.WHAT.MANUAL_GENERIC;
                } else if (thermoFunction == ThermoFunction.COOL) {
                    newWhat = Thermoregulation.WHAT.MANUAL_CONDITIONING;
                } else {
                    newWhat = Thermoregulation.WHAT.MANUAL_HEATING;
                }
                break;
            case PROTECTION:
                if (thermoFunction == ThermoFunction.GENERIC) {
                    newWhat = Thermoregulation.WHAT.PROTECTION_GENERIC;
                } else if (thermoFunction == ThermoFunction.COOL) {
                    newWhat = Thermoregulation.WHAT.PROTECTION_CONDITIONING;
                } else {
                    newWhat = Thermoregulation.WHAT.PROTECTION_HEATING;
                }
                break;
            case OFF:
                if (thermoFunction == ThermoFunction.GENERIC) {
                    newWhat = Thermoregulation.WHAT.OFF_GENERIC;
                } else if (thermoFunction == ThermoFunction.COOL) {
                    newWhat = Thermoregulation.WHAT.OFF_CONDITIONING;
                } else {
                    newWhat = Thermoregulation.WHAT.OFF_HEATING;
                }
                break;
        }
        return newWhat;
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
        logger.debug("==OWN:ThermoHandler== thingUpdated()");
    }

} /* class */
