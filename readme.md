# OrientUp Android

Applicazione Android (API 30) scritta in Kotlin per l'upload di un file XML di una competizione di orienteering verso un endpoint web (OrientUp Website).  

## Funzionamento
 - One shot: è possibile caricare il file selezionandolo direttamente e caricandolo una volta sola
 - As service: si avvia un servizio che monitora una cartella e carica il file se questo subisce modifiche, controllandolo periodicamente

## To do
 - Implementazione sistema di autenticazione una volta sistemato lato webservice
 - Sistemazione switch a modalità notturna
