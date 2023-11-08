package fwpx.cooja;

import fwpx.mspsim.mote.FwpxNode;
import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.IPAddress;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.mspmote.MspMoteType;
import org.contikios.cooja.mspmote.interfaces.*;
import se.sics.mspsim.core.MSP430;

import java.io.IOException;
import java.util.List;

@ClassDescription("Fwpx mote")
@AbstractionLevelDescription("Emulated level")
public class MoteType extends MspMoteType {

  @Override
  public MspMote generateMote(Simulation simulation) throws MoteTypeCreationException {
    MSP430 cpu;
    try {
      cpu = FwpxNode.makeCPU(FwpxNode.makeChipConfig(), fileFirmware.getAbsolutePath());
    } catch (IOException e) {
      throw new MoteTypeCreationException("Failed to create CPU", e);
    }
    return new Mote(this, simulation, new FwpxNode(cpu, new CoojaM25P80(cpu)));
  }

  @Override
  public String getMoteType() {
    return "fwpx";
  }

  @Override
  public String getMoteName() {
    return "Fwpx";
  }

  @Override
  protected String getMoteImage() {
    return null;
  }

  @Override
  public List<Class<? extends MoteInterface>> getDefaultMoteInterfaceClasses() {
    return List.of(
            Position.class,
            IPAddress.class,
            Mote2MoteRelations.class,
            MoteAttributes.class,
            MspClock.class,
            MspMoteID.class,
            Radio.class,
            MspSerial.class,
            MspLED.class,
            MspDebugOutput.class);
  }

  @Override
  public List<Class<? extends MoteInterface>> getAllMoteInterfaceClasses() {
    return List.of(
        Position.class,
        IPAddress.class,
        Mote2MoteRelations.class,
        MoteAttributes.class,
        MspClock.class,
        MspMoteID.class,
        Radio.class,
        MspSerial.class,
        MspLED.class,
        MspDebugOutput.class);
  }
}
