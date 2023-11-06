package fwpx.cooja;

import fwpx.mspsim.Packet;

public class RadioPacket implements org.contikios.cooja.RadioPacket {

  private final Packet packet;
  public RadioPacket (Packet packet) {
    this.packet = packet;
  }

  @Override
  public byte[] getPacketData() {
    return packet.data();
  }
}
