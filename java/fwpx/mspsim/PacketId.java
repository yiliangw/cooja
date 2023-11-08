package fwpx.mspsim;

public record PacketId(Transceiver trx, byte channel, long seqNum) {}
