package fwpx.cooja;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

public class RadioConnection extends org.contikios.cooja.RadioConnection {
  private static final Logger logger = LoggerFactory.getLogger(org.contikios.cooja.RadioConnection.class);

  private static class DestInfo {
    long latency; /* Propagation latency in microseconds */
    double signalStrength;
    DestInfo(double signalStrength, long latency) {
      this.latency = latency;
      this.signalStrength = signalStrength;
    }
  }

  private final Radio source;
  private final int channel;

  private final Map<Radio, DestInfo> destinations = new HashMap<>();

  public RadioConnection(Radio source, int channel) {
    super(source);
    this.source = source;
    this.channel = channel;
  }

  @Override
  public void addDestination(org.contikios.cooja.interfaces.Radio radio, Long delay) {
    logger.error("Not supported");
  }

  @Override
  public void addDestination(org.contikios.cooja.interfaces.Radio radio) {
    logger.error("Not supported");
  }

  @Override
  public void removeDestination(org.contikios.cooja.interfaces.Radio radio) {
    destinations.remove(radio);
  }

  @Override
  public void addInterfered(org.contikios.cooja.interfaces.Radio radio) {
    logger.error("Not supported");
  }

  @Override
  public boolean isDestination(org.contikios.cooja.interfaces.Radio radio) {
    return destinations.containsKey(radio);
  }

  @Override
  public boolean isInterfered(org.contikios.cooja.interfaces.Radio radio) {
    if (!destinations.containsKey(radio)) {
      logger.error("Radio is not a connection destination: " + radio);
    }
    return false;
  }

  @Override
  public Radio getSource() {
    return source;
  }

  @Override
  public Radio[] getDestinations() {
    return destinations.keySet().toArray(new Radio[0]);
  }

  @Override
  public Radio[] getAllDestinations() {
    return getDestinations();
  }

  @Override
  public Radio[] getInterfered() {
    return new Radio[0];
  }

  @Override
  public Radio[] getInterferedNonDestinations() {
    return new Radio[0];
  }

  public void fwpxAddDestination(Radio radio, double signalStrength, long latency) {
    if (radio == null) {
      logger.error("Only supports FwpxRadio.");
      return;
    }
    destinations.put(radio, new DestInfo(signalStrength, latency));
  }

  public void fwpxAddDestination(Radio radio, double signalStrength) {
    fwpxAddDestination(radio, signalStrength, 0L);
  }

  @Override
  public long getDestinationDelay(org.contikios.cooja.interfaces.Radio radio) {
    var info = destinations.get(radio);
    if (info == null) {
      logger.error("Radio is not a connection destination: " + radio);
      return 0;
    }
    return info.latency;
  }

  public double getDestinationSignalStrength(Radio radio) {
    var info = destinations.get(radio);
    if (info == null) {
      logger.error("Radio is not a connection destination: " + radio);
      return 0;
    }
    return info.signalStrength;
  }

  public int getChannel() {
    return channel;
  }
}
