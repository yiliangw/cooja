package fwpx.mspsim;

public interface PacketSource {
  void addPacketListener(PacketListener listener);
  void removePacketListener(PacketListener listener);
}
