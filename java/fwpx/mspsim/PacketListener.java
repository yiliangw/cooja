package fwpx.mspsim;

import se.sics.mspsim.util.ProxySupport;

public interface PacketListener {
  void packetDeliveryStart(PacketId id);
  void packetDeliveryEnd(PacketId id, Packet packet);

  class Proxy extends ProxySupport<PacketListener> implements PacketListener {
    static final Proxy INSTANCE = new Proxy();

    @Override
    public void packetDeliveryStart(PacketId id) {
      PacketListener[] listeners = this.listeners;
      for(PacketListener listener : listeners) {
        listener.packetDeliveryStart(id);
      }
    }

    @Override
    public void packetDeliveryEnd(PacketId id, Packet packet) {
      PacketListener[] listeners = this.listeners;
      for(PacketListener listener : listeners) {
        listener.packetDeliveryEnd(id, packet);
      }
    }
  }
}
