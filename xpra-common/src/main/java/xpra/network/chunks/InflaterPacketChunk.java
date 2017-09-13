/*
 * Copyright (C) 2017 Jakub Ksiezniak
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package xpra.network.chunks;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import xpra.network.XpraConnector;

public class InflaterPacketChunk implements StreamChunk {

	private final int packetSize;
	private final StreamChunk parent;
	
	private final Inflater inflater = new Inflater();

	public InflaterPacketChunk(byte compressionLevel, int packetSize, StreamChunk parent) {
		this.packetSize = packetSize;
		this.parent = parent;
	}

	@Override
	public StreamChunk readChunk(InputStream is, XpraConnector connector) throws IOException {
		final ChunkInflaterInputStream inflaterInputStream = new ChunkInflaterInputStream(is, inflater, packetSize);
		final StreamChunk chunk = parent.readChunk(inflaterInputStream, connector);
		inflaterInputStream.drain();
		return chunk;
	}

	private static class ChunkInflaterInputStream extends InflaterInputStream {

		private int bytesToDrain;

		public ChunkInflaterInputStream(InputStream in, Inflater inf, int size) {
			super(in, inf, size);
			this.bytesToDrain = size;
		}

		@Override
		protected void fill() throws IOException {
			final int toRead = Math.min(buf.length, bytesToDrain);
			len = in.read(buf, 0, toRead);
			if (len == -1) {
				throw new EOFException("Unexpected end of ZLIB input stream");
			}
			inf.setInput(buf, 0, len);
			bytesToDrain -= len;
		}

		public void drain() throws IOException {
			if (bytesToDrain < 0) {
				throw new IllegalArgumentException("negative skip length");
			}
			int toRead = bytesToDrain;
			while (toRead > 0) {
				int len = toRead;
				if (len > buf.length) {
					len = buf.length;
				}
				len = read(buf, 0, len);
				if (len == -1) {
					break;
				}
				toRead -= len;
			}
		}
		
		@Override
		public int available() throws IOException {
			return inf.finished() ? 0 : 1;
		}
	}
}
