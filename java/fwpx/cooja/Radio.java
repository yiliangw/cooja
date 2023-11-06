package fwpx.cooja;

import fwpx.mspsim.Packet;
import fwpx.mspsim.PacketId;
import fwpx.mspsim.PacketListener;
import fwpx.mspsim.Transceiver;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.mspmote.MspMote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class Radio extends org.contikios.cooja.interfaces.Radio implements CustomDataRadio, PacketListener {
  private static final Logger logger = LoggerFactory.getLogger(Radio.class);

  private static abstract class TxStateChange {}

  private static class TxStart extends TxStateChange {
    final PacketId id;
    TxStart(PacketId id) {
      this.id = id;
    }
  }

  private static class TxEnd extends TxStateChange {
    final PacketId id;
    final RadioPacket packet;
    TxEnd(PacketId id, RadioPacket packet) {
      this.id = id;
      this.packet = packet;
    }
  }

  final MspMote mote;
  final Transceiver trx;

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;

  private boolean isTransmitting = false;

  private PacketId outgoingPacketId = null;
  private RadioPacket lastOutgoingPacket = null;

  private TxStateChange lastTxStateChangeTransmitted = null;
  private TxStateChange lastTxStateChangeReceived = null;

  private long lastDeliveryTime = -1;

  private final Random random;

  public Radio(Mote m) {
    this.mote = (MspMote) m;
    this.random = m.getSimulation().getRandomGenerator();
    this.trx = this.mote.getCPU().getChip(Transceiver.class);
    if (this.trx == null) {
      throw new IllegalStateException("Mote is not equipped with an IEEE 802.15.4 radio");
    }

    trx.addPacketListener(new PacketListener() {
      @Override
      public void packetDeliveryStart(PacketId id) {
        if (isTransmitting()) {
          logger.error("Detected concurrent transmitting");
          return;
        }

        /* This asks the medium to set up the connection. */
        lastEvent = RadioEvent.TRANSMISSION_STARTED;
        outgoingPacketId = id;
        lastOutgoingPacket = null;
        isTransmitting = true;
        radioEventTriggers.trigger(lastEvent, Radio.this);

        /* This sends the notification through the connection. */
        lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        lastTxStateChangeTransmitted = new TxStart(id);
        radioEventTriggers.trigger(lastEvent, Radio.this);
      }

      @Override
      public void packetDeliveryEnd(PacketId id, Packet packet) {
        if (!isTransmitting()) {
          logger.error("packetDeliveryEnd when not transmitting");
          return;
        }

        if (!id.equals(outgoingPacketId)) {
          logger.error("outgoingPacketId does not match");
          return;
        }

        /* This sends the notification through the connection. */
        lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        outgoingPacketId = null;
        lastOutgoingPacket = new RadioPacket(packet);
        isTransmitting = false;
        lastTxStateChangeTransmitted = new TxEnd(id, lastOutgoingPacket);
        radioEventTriggers.trigger(lastEvent, Radio.this);

        /* This asks the medium to clear up the connection. */
        lastEvent = RadioEvent.TRANSMISSION_FINISHED;
        radioEventTriggers.trigger(lastEvent, Radio.this);
      }
    }); /* addPacketListener */

    /* TODO: trx.addOperatingModeListener */
  }

  /*****************************************************************************
   * Radio implementation
   *****************************************************************************/

  @Override
  public void signalReceptionStart() {}

  @Override
  public void signalReceptionEnd() {}

  @Override
  public RadioPacket getLastPacketTransmitted() {
    return lastOutgoingPacket;
  }

  @Override
  public boolean isTransmitting() {
    return isTransmitting;
  }

  /*****************************************************************************
   * CustomDataRadio implementation
   *****************************************************************************/

  @Override
  public boolean canReceiveFrom(CustomDataRadio radio) {
    return radio instanceof Radio;
  }

  @Override
  public void receiveCustomData(Object data) {
    if (data instanceof TxStateChange change) {
      lastTxStateChangeReceived = change;
      if (data instanceof TxStart ts) {
        /* TODO: */
      } else if (data instanceof TxEnd te) {
        /* TODO: */
      } else {
        logger.error("Unknown TxStateChange object: {}", data);
      }
    } else {
      logger.error("Received unknown custom data: {}", data);
    }
  }

  @Override
  public TxStateChange getLastCustomDataTransmitted() {
    return lastTxStateChangeTransmitted;
  }

  @Override
  public TxStateChange getLastCustomDataReceived() {
    return lastTxStateChangeReceived;
  }
}
