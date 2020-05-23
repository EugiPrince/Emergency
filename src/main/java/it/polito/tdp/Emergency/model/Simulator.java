package it.polito.tdp.Emergency.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import it.polito.tdp.Emergency.model.Event.EventType;
import it.polito.tdp.Emergency.model.Paziente.CodiceColore;

public class Simulator {

	// PARAMETRI DI SIMULAZIONE
	private int NS = 5; // numero studi medici

	//Ho i setters per questi due, ma inserisco comunque dei valori di default (buona norma)
	private int NP = 150; // numero di pazienti
	private Duration T_ARRIVAL = Duration.ofMinutes(5); // intervallo tra i pazienti
	
	//Tutte variabili che definiamo come costanti, sono dati del problema che non variano
	private final Duration DURATION_TRIAGE = Duration.ofMinutes(5);
	
	private final Duration DURATION_WHITE = Duration.ofMinutes(10);
	private final Duration DURATION_YELLOW = Duration.ofMinutes(15);
	private final Duration DURATION_RED = Duration.ofMinutes(30);

	private final Duration TIMEOUT_WHITE = Duration.ofMinutes(90);
	private final Duration TIMEOUT_YELLOW = Duration.ofMinutes(30);
	private final Duration TIMEOUT_RED = Duration.ofMinutes(60);

	private final LocalTime oraInizio = LocalTime.of(8, 0); // apertura
	private final LocalTime oraFine = LocalTime.of(20, 0); // chiusura
	
	private final Duration TICK_TIME = Duration.ofMinutes(5); //Controllo ogni tot per vedere se c'è uno studio libero

	// OUTPUT DA CALCOLARE, su cui possiamo fare delle statistiche
	private int pazientiTot;
	private int pazientiDimessi;
	private int pazientiAbbandonano;
	private int pazientiMorti;

	// STATO DEL SISTEMA
	//Deve rappresentare i pazienti che sono presenti all'interno della simulazione
	private List<Paziente> pazienti;
	private PriorityQueue<Paziente> attesa ; // post-triage prima di essere chiamati, uso la PQ così li ordino
	private int studiLiberi ;

	private CodiceColore coloreAssegnato;

	// CODA DEGLI EVENTI
	private PriorityQueue<Event> queue;

	// INIZIALIZZAZIONE
	public void init() {
		this.queue = new PriorityQueue<>();
		
		this.pazienti = new ArrayList<>();
		this.attesa = new PriorityQueue<>();

		this.pazientiTot = 0;
		this.pazientiDimessi = 0;
		this.pazientiAbbandonano = 0;
		this.pazientiMorti = 0;
		
		this.studiLiberi = this.NS ;

		this.coloreAssegnato = CodiceColore.WHITE;

		// generiamo eventi iniziali
		int nPaz = 0;
		LocalTime oraArrivo = this.oraInizio; //Il primo paziente arriva quando apre

		while (nPaz < this.NP && oraArrivo.isBefore(this.oraFine)) {
			Paziente p = new Paziente(oraArrivo, CodiceColore.UNKNOWN); // appena arriva non so che codice dargli
			this.pazienti.add(p);
			Event e = new Event(oraArrivo, EventType.ARRIVAL, p);
			queue.add(e);

			nPaz++;
			oraArrivo = oraArrivo.plus(T_ARRIVAL);
		}
		
		// Genera TICK iniziale
		queue.add(new Event(this.oraInizio, EventType.TICK, null));
	}

	public int getPazientiAbbandonano() {
		return pazientiAbbandonano;
	}

	public int getPazientiTot() {
		return pazientiTot;
	}

	public int getPazientiDimessi() {
		return pazientiDimessi;
	}

	public int getPazientiMorti() {
		return pazientiMorti;
	}

	// ESECUZIONE
	public void run() {
		// così i pazienti possono entrare fino alle 20 e poi la simulazione continua
		while (!this.queue.isEmpty()) { // se volessi mandare via tutti dovrei aggiungere il controllo nel while
			Event e = this.queue.poll(); // con && e.time < time di chiusura
			System.out.println(e + " Free studios "+this.studiLiberi);
			processEvent(e);
		}
	}

	private void processEvent(Event e) {

		Paziente paz = e.getPaziente();

		switch (e.getType()) {
		case ARRIVAL:
			// arriva un paziente: tra 5 minuti sarà finito il triage, quindi ci sarà un nuovo evento da inserire in coda
			queue.add(new Event(e.getTime().plus(DURATION_TRIAGE), 
					EventType.TRIAGE, paz));
			this.pazientiTot++;
			break;

		case TRIAGE:
			// assegna codice colore
			paz.setColore(nuovoCodiceColore());
			
			// mette in lista d'attesa
			attesa.add(paz) ;

			// schedula timeout, valore che dipende dal colore che è stato assegnato
			if(paz.getColore()==CodiceColore.WHITE)
				queue.add(new Event(e.getTime().plus(TIMEOUT_WHITE),
						EventType.TIMEOUT, paz));
			else if(paz.getColore()==CodiceColore.YELLOW)
				queue.add(new Event(e.getTime().plus(TIMEOUT_YELLOW),
						EventType.TIMEOUT, paz));
			else if(paz.getColore()==CodiceColore.RED)
				queue.add(new Event(e.getTime().plus(TIMEOUT_RED),
						EventType.TIMEOUT, paz));
			break;

		case FREE_STUDIO: //Sta entrando un paziente (?)
			if(this.studiLiberi==0) // non ci sono studi liberi
				break ;
			Paziente prossimo = attesa.poll();
			if(prossimo != null) {
				// fallo entrare
				this.studiLiberi-- ;
				
				// schedula uscita dallo studio
				if(prossimo.getColore()==CodiceColore.WHITE)
					queue.add(new Event(e.getTime().plus(DURATION_WHITE),
							EventType.TREATED, prossimo));
				else if(prossimo.getColore()==CodiceColore.YELLOW)
					queue.add(new Event(e.getTime().plus(DURATION_YELLOW),
							EventType.TREATED, prossimo));
				else if(prossimo.getColore()==CodiceColore.RED)
					queue.add(new Event(e.getTime().plus(DURATION_RED),
							EventType.TREATED, prossimo));
			}
			break;
			
		case TREATED:
			// libera lo studio, perchè il paziente ha finito ed è stato dimesso
			this.studiLiberi++ ;
			paz.setColore(CodiceColore.OUT);
			
			this.pazientiDimessi++;
			this.queue.add(new Event(e.getTime(), EventType.FREE_STUDIO, null)); // Non so chi sia il paziente
			break;
			
		case TIMEOUT:
			// esci dalla lista d'attesa
			attesa.remove(paz); // c'è ancora nella lista dei pazienti, ma non più in quella della lista d'attesa
			if(paz.getColore()==CodiceColore.OUT)
				break;
			
			switch(paz.getColore()) {
			case WHITE:
				//va a casa
				this.pazientiAbbandonano++;
				break;
			case YELLOW:
				// diventa RED
				paz.setColore(CodiceColore.RED);
				attesa.add(paz);
				queue.add(new Event(e.getTime().plus(TIMEOUT_RED),
						EventType.TIMEOUT, paz));
				break;
			case RED:
				// muore
				this.pazientiMorti++;
				paz.setColore(CodiceColore.OUT);

				break;
			}
			break;
			
		case TICK:
			if(this.studiLiberi > 0) { //Appena puoi fai entrare un paziente
				this.queue.add(new Event(e.getTime(),
						EventType.FREE_STUDIO, null));
			}
			
			if(e.getTime().isBefore(LocalTime.of(23, 30)))
				this.queue.add(new Event(e.getTime().plus(this.TICK_TIME),
					EventType.TICK, null));
			break;
		}
	}

	//Viene implementata la logica di rotazione per l'incremento del colore assegnato.. parte da bianco e va avanti
	private CodiceColore nuovoCodiceColore() {
		CodiceColore nuovo = coloreAssegnato;

		if (coloreAssegnato == CodiceColore.WHITE)
			coloreAssegnato = CodiceColore.YELLOW;
		else if (coloreAssegnato == CodiceColore.YELLOW)
			coloreAssegnato = CodiceColore.RED;
		else
			coloreAssegnato = CodiceColore.WHITE;

		return nuovo;
	}

	public int getNS() {
		return NS;
	}

	public void setNS(int nS) {
		NS = nS;
	}

	public int getNP() {
		return NP;
	}

	public void setNP(int nP) {
		NP = nP;
	}

	public Duration getT_ARRIVAL() {
		return T_ARRIVAL;
	}

	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}
}
