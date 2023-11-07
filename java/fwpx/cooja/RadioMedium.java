package fwpx.cooja;

import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.radiomediums.DGRMDestinationRadio;
import org.contikios.cooja.radiomediums.DestinationRadio;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

public class RadioMedium implements org.contikios.cooja.RadioMedium {
  private static final Logger logger = LoggerFactory.getLogger(RadioMedium.class);

  /* Signal strengths in dBm.
   * Approx. values measured on TmoteSky */
  public static final double SS_NOTHING = -100;
  public static final double SS_STRONG = -10;
  public static final double SS_WEAK = -95;

  public double TRANSMITTING_RANGE = 50; /* Transmission range. */
  /* TODO: Interference range? i.e. transmissions that are unlikely to be recovered. I think it could be
     better to manage this with radio by attaching the supposed receiving strengths of the packets. */

  private final ArrayList<Radio> registeredRadios = new ArrayList<>();

  private final ArrayList<RadioConnection> activeConnections = new ArrayList<>();
  private RadioConnection lastConnection = null;

  private final Simulation simulation;

  private final BiConsumer<org.contikios.cooja.interfaces.Radio.RadioEvent, org.contikios.cooja.interfaces.Radio> radioEventsObserver;

  private final EventTriggers<EventTriggers.AddRemove, org.contikios.cooja.interfaces.Radio> radioMediumTriggers = new EventTriggers<>();
  private final EventTriggers<org.contikios.cooja.interfaces.Radio.RadioEvent, Object> radioTransmissionTriggers = new EventTriggers<>();

  private final DirectedGraphMedium dgrm; /* Used only for efficient destination lookup */

  private final Random random;

  public RadioMedium(Simulation simulation) {
    this.simulation = simulation;
    this.random = simulation.getRandomGenerator();

    this.radioEventsObserver = (event, radio_) -> {
      Radio radio = convertRadio(radio_);
      switch(event) {
        case HW_ON:
        break;
        case HW_OFF:
        break;
        case TRANSMISSION_STARTED: {
          int channel = radio.getOutgoingPacketId().channel();
          var newConn = createConnection(radio, channel);

          var newConnection = createConnection(radio, channel);
          if (newConnection != null) {
            activeConnections.add(newConnection);
            /* Do not consider the propagation latency now. Also, we don't need to notify with
             * signalReceptionStart() here. Instead, we are expecting that the CustomObjects
             * will do the notification.
             */
          }
          lastConnection = null;
          radioTransmissionTriggers.trigger(Radio.RadioEvent.TRANSMISSION_STARTED, null);
        }
        break;
        case TRANSMISSION_FINISHED: {
          /* This should come after the TxEnd CustomObject, since we rely on the connection
           * to deliver the TxEnd.
           */
          var connection = getActiveConnectionFrom(radio);
          if (connection == null) {
            logger.warn("No active connection for radio: " + radio);
            return;
          }
          activeConnections.remove(connection);
          lastConnection = connection;
          radioTransmissionTriggers.trigger(Radio.RadioEvent.TRANSMISSION_FINISHED, null);
        }
        break;
        case CUSTOM_DATA_TRANSMITTED: {
          var connection = getActiveConnectionFrom(radio);
          if (connection == null) {
            logger.warn("No active connection for radio: " + radio);
            return;
          }
          var data = radio.getLastCustomDataTransmitted();
          if (data == null) {
            logger.error("No custom data to foraward");
            return;
          }
          for (var dstRadio : connection.getAllDestinations()) {
            /* We do not consider the propagation latency now. */
            dstRadio.receiveCustomData(data);
          }
        }
        break;
        default:
          /* RECEPTION_STARTED, RECEPTION_FINISHED, RECEPTION_INTERFERED, PACKET_TRANSMITTED, UNKNOWN */
          logger.warn("Unhandled event {} from {}", event, radio);
      }
    };

    /* Leverage DGRM to manage the potential links. */
    this.dgrm = new DirectedGraphMedium(simulation) {
      @Override
      protected void analyzeEdges() {
        /* Create edges according to distances. This graph represents all the potential links.
         * XXX May be slow for mobile networks */
        clearEdges();
        var registeredRadios = RadioMedium.this.getRegisteredRadios();
        for (Radio source : registeredRadios) {
          Position sourcePos = source.getPosition();
          for (Radio dest : registeredRadios) {
            if (source == dest) continue;
            Position destPos = dest.getPosition();
            double distance = sourcePos.getDistanceTo(destPos);
            if (distance <= TRANSMITTING_RANGE) {
              addEdge(new DirectedGraphMedium.Edge(source, new DGRMDestinationRadio(dest)));
            }
          }
        }
        super.analyzeEdges();
      }
    }; /* DirectedGraphMedium */

    /* Re-analyze potential receivers on position changes. */
    simulation.getEventCentral().getPositionTriggers().addTrigger(this, (o, m) -> this.dgrm.requestEdgeAnalysis());
    /* Re-analyze potential receivers on mote registration. */
    simulation.getMoteTriggers().addTrigger(this, (o, m) -> dgrm.requestEdgeAnalysis());

    dgrm.requestEdgeAnalysis();
  }

  @Override
  public void registerRadioInterface(org.contikios.cooja.interfaces.Radio fwpxradio, Simulation sim) {
    Radio radio = convertRadio(fwpxradio);
    if (radio == null) {
      logger.warn("No radio to register");
      return;
    }

    registeredRadios.add(radio);
    radio.getRadioEventTriggers().addTrigger(this, radioEventsObserver);
    radioMediumTriggers.trigger(EventTriggers.AddRemove.ADD, radio);
  }

  @Override
  public void unregisterRadioInterface(org.contikios.cooja.interfaces.Radio fwpxradio, Simulation sim) {
    Radio radio = convertRadio(fwpxradio);
    if (!registeredRadios.contains(radio)) {
      logger.warn("No radio to unregister: " + radio);
    }
    radio.getRadioEventTriggers().removeTrigger(this, radioEventsObserver);
    registeredRadios.remove(radio);

    removeFromActiveConnections(radio);
    radioMediumTriggers.trigger(EventTriggers.AddRemove.REMOVE, radio);
  }

  @Override
  public List<org.contikios.cooja.interfaces.Radio> getNeighbors(org.contikios.cooja.interfaces.Radio sourceRadio) {
    var list = new ArrayList<org.contikios.cooja.interfaces.Radio>();
    var sourceRadioPosition = sourceRadio.getPosition();
    double moteTransmissionRange = TRANSMITTING_RANGE
        * ((double) sourceRadio.getCurrentOutputPowerIndicator() / (double) sourceRadio.getOutputPowerIndicatorMax());
    for (var radio : dgrm.getPotentialDestinations(sourceRadio)) {
      if (radio.radio == sourceRadio) {
        continue;
      }
      double distance = sourceRadioPosition.getDistanceTo(radio.radio.getPosition());
      if (distance <= moteTransmissionRange) {
        list.add(radio.radio);
      }
    }
    return list;
  }

  @Override
  public EventTriggers<org.contikios.cooja.interfaces.Radio.RadioEvent, Object> getRadioTransmissionTriggers() {
    return radioTransmissionTriggers;
  }

  @Override
  public RadioConnection getLastConnection() {
    return lastConnection;
  }

  @Override
  public Collection<Element> getConfigXML() {
    /* TODO */
    return null;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    /* TODO */
    return false;
  }

  public Radio[] getRegisteredRadios() {
    return registeredRadios.toArray(new Radio[0]);
  }

  private RadioConnection getActiveConnectionFrom(Radio source) {
    for (var conn : activeConnections) {
      if (conn.getSource() == source)
        return conn;
    }
    return null;
  }

  /**
   * @implSepc  After calling the function, the radio will not be notified with any transmission event,
   *            even there are ongoing transmissions. In other words, the radio should properly reset
   *            the receiving state when this function is called.
   */
  private void removeFromActiveConnections(Radio radio) {
    boolean isDestination = false;
    for (var conn : activeConnections) {
      if (conn.isDestination(radio)) {
        conn.removeDestination(radio);
        isDestination = true;
      }
    }
    if (isDestination) {
      radio.interfereAnyReception();
    }
  }

  private Radio convertRadio(org.contikios.cooja.interfaces.Radio radio) {
    if (radio instanceof Radio fwpxRadio) {
      return fwpxRadio;
    } else {
      logger.error("Unexpected radio: " + radio);
      return null;
    }
  }

  private RadioConnection createConnection(Radio source, int channel) {
    RadioConnection conn = new RadioConnection(source, channel);

    var txSuccessP = getTxSuccessProbability();
    if (txSuccessP < 1.0 && random.nextDouble() > txSuccessP) {
      /* Fail radio transmission randomly - no radios will hear this transmission */
      return conn;
    }

    /* Calculate ranges: grows with radio output power */
    double moteTransmissionRange = TRANSMITTING_RANGE
        * ((double) source.getCurrentOutputPowerIndicator() / (double) source.getOutputPowerIndicatorMax());

    /* Get all potential destination radios */
    DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(source);
    if (potentialDestinations == null) {
      return conn;
    }

    /* Loop through all potential destinations */
    Position sourcePos = source.getPosition();
    for (DestinationRadio dest: potentialDestinations) {
      var r = (Radio) dest.radio;

      Position destPos = r.getPosition();

      double distance = sourcePos.getDistanceTo(destPos);
      if (distance <= moteTransmissionRange) {
        /* Within transmission range. The propagation latency is not considered now. */
        /* TODO: Calculate the signal strength according to the distance.  */
        conn.fwpxAddDestination(r, SS_STRONG);
      }
    }
    return conn;
  }

  /* TODO */
  private double getTxSuccessProbability() {
    return 1.0;
  }
}
