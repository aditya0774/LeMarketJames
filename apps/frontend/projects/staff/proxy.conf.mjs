// Dev-server proxy for `ng serve staff` against services on this machine (see staff-proxy.mjs).
import { staffProxy } from './staff-proxy.mjs';

export default staffProxy('localhost');
