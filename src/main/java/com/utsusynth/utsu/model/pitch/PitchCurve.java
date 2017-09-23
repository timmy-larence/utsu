package com.utsusynth.utsu.model.pitch;

import java.util.HashMap;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** 
 * Stores up to one pitchbend for each "pitch step" in a song.  There are always 96 pitch steps
 * per beat, regardless of tempo.
 */
public class PitchCurve {
	// Map of pitch step number to attached pitchbend, if any.
	// TODO: Limit the minimum and maximum x-values of portamento/vibrato.
	private final HashMap<Integer, Pitchbend> pitchbends;
	private final PortamentoFactory portamentoFactory;
	
	// TODO: Put this in a provider.
	public PitchCurve() {
		this.pitchbends = new HashMap<Integer, Pitchbend>();
		this.portamentoFactory = new PortamentoFactory();
	}
	
	/** Adds pitchbends for a single note. */
	public void addPitchbends(
			int noteStartMs, PitchbendData data, int prevNoteNum, int curNoteNum) {
		if (data.getPBS().isEmpty() || data.getPBW().isEmpty()) {
			// TODO: Handle this.
			return;
		}
		// Start x value (in milliseconds) and y value (in cents) of a pitchbend.
		double startMs = noteStartMs + data.getPBS().get(0);
		double pitchStart = prevNoteNum * 10; // Measured in tenths (1/10 a semitone.)
		ImmutableList<Double> pbw = data.getPBW();
		ImmutableList<Double> pby = data.getPBY();
		ImmutableList<String> pbm = data.getPBM();
		
		// Parse each pitchbend from provided values.
		for (int i = 0; i < data.getPBW().size(); i++) {
			double endMs = startMs + pbw.get(i);
			double pitchEnd = curNoteNum * 10;
			if (pby.size() >= i + 1) {
				pitchEnd += pby.get(i);
			}
			String pitchShape = "";
			if (pbm.size() >= i + 1) {
				pitchShape = pbm.get(i);
			}
			Portamento portamento = portamentoFactory.makePortamento(
					noteStartMs, startMs, pitchStart, endMs, pitchEnd, pitchShape);
			
			// Add portamento to all affected steps on the pitch curve.
			for (int j = nextPitchStep(startMs); j <= prevPitchStep(endMs); j++) {
				if (pitchbends.containsKey(j)) {
					pitchbends.get(j).addPortamento(portamento);
				} else {
					pitchbends.put(j, Pitchbend.makePitchbend(portamento));
				}
			}
			// End of the current pitchbend is the start of the next one.
			startMs = endMs;
			pitchStart = pitchEnd;
		}
	}
	
	/** Removes pitchbends for a single note. */
	public void removePitchbends(int noteStartMs, PitchbendData data) {
		if (data.getPBS().isEmpty() || data.getPBW().isEmpty()) {
			// TODO: Handle this.
			return;
		}
		double startMs = noteStartMs + data.getPBS().get(0);
		double endMs = startMs;
		for (double width : data.getPBW()) {
			endMs += width;
		}
		for (int i = nextPitchStep(startMs); i <= prevPitchStep(endMs); i++) {
			// Remove portamento from each pitch step it covers.
			if (pitchbends.containsKey(i)) {
				Pitchbend pitchbend = pitchbends.get(i);
				pitchbend.removePortamento();
				if (pitchbend.isEmpty()) {
					pitchbends.remove(i);
				}
			}
		}
	}
	
	/** Writes out pitchbends for a section into a format readable by resamplers. */
	public String renderPitchbends(int firstStep, int lastStep, int noteNum) {
		String result = "";
		double noteNumPitch = noteNum * 10; // In tenths. (1/10 of a semitone)
		double defaultPitch = 0; // In tenths. (1/10 of a semitone)
		for (int scanStep = firstStep; scanStep <= lastStep; scanStep++) {
			// Scan through the steps until first default pitch is found.
			if (pitchbends.containsKey(scanStep)) {
				Optional<Portamento> portamento = pitchbends.get(scanStep).getPortamento();
				if (portamento.isPresent()) {
					defaultPitch = portamento.get().getStartPitch();
					break;
				}
			}
		}
		
		for (int step = firstStep; step <= lastStep; step++) {
			if (pitchbends.containsKey(step)) {
				// Write pitchbend.
				int positionMs = step * 5; // 92 pitch steps in a beat of 480 ms.
				double realPitch = pitchbends.get(step).apply(positionMs); // In tenths.
				int diff = (int) ((realPitch - noteNumPitch) * 10); // In cents.
				result += convertTo12Bit(diff);
				
				// Set the default pitch to the one at the end of current portamento.
				Optional<Portamento> portamento = pitchbends.get(step).getPortamento();
				if (portamento.isPresent()) {
					defaultPitch = portamento.get().getEndPitch();
				}
			} else {
				// Write a stretch of no pitchbends.
				int numEmpty = 0;
				int emptyStep = step;
				for (; emptyStep <= lastStep; emptyStep++) {
					if (pitchbends.containsKey(emptyStep)) {
						break;
					} else {
						numEmpty++;
					}
				}
				int diff = (int) ((defaultPitch - noteNumPitch) * 10); // In cents.
				result += convertTo12Bit(diff);
				if (numEmpty > 1) {
					result += String.format("#%d#", numEmpty - 1);
				}
				step = emptyStep - 1; // Move step to the end of the empty stretch.
			}
		}
		return result;
	}
	
	/**
	 * For some reason, resamplers want two characters that represent a 12-bit number in
	 * two's complement form (-2048 to 2047). I would not be using this format if existing
	 * resamplers didn't require it.
	 * @return A string of length 2 representing a 12-bit number.
	 */
	private static String convertTo12Bit(int convertMe) {
		// Convert out of two's complement form.
		if (convertMe < 0) {
			convertMe += 4096;
		}
		// Make sure convertMe is between 0 and 4095.
		convertMe = Math.max(0, Math.min(4095, convertMe));
		String result = ""; // Set to 0 by default.
		for (int sixBitNumber : ImmutableList.of(convertMe / 64, convertMe % 64)) {
			if (sixBitNumber >= 0 && sixBitNumber < 26) {
				result += (char) (sixBitNumber + 'A');
			} else if (sixBitNumber >= 26 && sixBitNumber < 52) {
				result += (char) (sixBitNumber - 26 + 'a');
			} else if (sixBitNumber >= 52 && sixBitNumber < 62) {
				result += (char) (sixBitNumber - 52 + '0');
			} else if (sixBitNumber == 62) {
				result += '+';
			} else if (sixBitNumber == 63) {
				result += '/';
			} else {
				// Return 0 if the number is not in range [0, 64).
				return "AA";
			}
		}
		if (result.length() != 2) {
			return "AA";
		}
		return result;
	}
	
	// Finds the pitch step just after this position.
	private static int nextPitchStep(double positionMs) {
		return ((int) Math.ceil(positionMs / 5.0));
	}
	
	// Returns the pitch step just before this position.
	private static int prevPitchStep(double positionMs) {
		int prevStep = ((int) Math.floor(positionMs / 5.0));
		if (prevStep == nextPitchStep(positionMs)) {
			// Do not let prevPitchStep and nextPitchStep return the same value.
			return prevStep - 1;
		}
		return prevStep;
	}
}
