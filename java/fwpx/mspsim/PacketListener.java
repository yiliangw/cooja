package fwpx.mspsim;

import se.sics.mspsim.util.ProxySupport;

public interface PacketListener {
  void packetDeliveryStarted(PacketId id);
  void packetDeliveryFinished(PacketId id, Packet packet);

  class Proxy extends ProxySupport<PacketListener> implements PacketListener {
    static final Proxy INSTANCE = new Proxy();

    @Override
    public void packetDeliveryStarted(PacketId id) {
      PacketListener[] listeners = this.listeners;
      for(PacketListener listener : listeners) {
        listener.packetDeliveryStarted(id);
      }
    }

    @Override
    public void packetDeliveryFinished(PacketId id, Packet packet) {
      PacketListener[] listeners = this.listeners;
      for(PacketListener listener : listeners) {
        listener.packetDeliveryFinished(id, packet);
      }
    }
  }
}
