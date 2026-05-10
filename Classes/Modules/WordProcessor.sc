// WordProcessor : VoxModule {
// var <>text, <>mode, <>offset, <>oneReplace, <>zeroReplace, <>oneMultiplier, <>zeroMultiplier, <>all;
//
// *new { |text="hello, world", mode=\ascii, offset=0, oneReplace=\sequence, zeroReplace, oneMultiplier=1, zeroMultiplier=1, all=true|
// ^super.new.init(text, offset, oneReplace, zeroReplace, oneMultiplier, zeroMultiplier, all);
// }
//
// init { |text, offset, oneReplace, zeroReplace, oneMultiplier, zeroMultiplier, all|
// this.text = text;
// this.mode = mode;
// this.offset = offset;
// this.oneReplace = oneReplace;
// this.zeroReplace = zeroReplace;
// this.oneMultiplier = oneMultiplier;
// this.zeroMultiplier = zeroMultiplier;
// this.all = all;
//
// ^this
// }
//
// doProcess { |vox|
//
// // perform ascii or morseselect according to mode
// // either we're removing events as replacement (if replacement is nil) or we're substituting with some sequence
// // if substitute is Box, substitute with Box.events (copies ... or call box.out ...) ... otherwise it's an array of some kind ... but they need to be events, and if not, this won't work
//
// // switch on mode ...
//
//
// var events = vox.events.collect { |ev|
// var currentTime = ev.absTime;
// var rt = ev.dur.rtdivide(divisions, prob_map, depth);
// var divided;
//
// if (rt.isKindOf(Array)) {
// rt.collect({
// arg dur;
// var newEv = ev.copy;
//
// newEv.dur = dur.value;
// newEv.absTime = currentTime;
// newEv.metadata = Dictionary.newFrom([\midinote_origin, ev.midinote]);
// currentTime = currentTime + dur.value;
//
// newEv.postln;
//
// newEv
// })
// } {
// ev.copy
// }
// };
//
// ^Vox.new(
// events.flatten,
// vox.metremap,
// vox.label,
// vox.metadata.copy
// )
// }
// }