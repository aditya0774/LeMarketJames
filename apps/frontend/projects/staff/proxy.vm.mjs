// Dev-server proxy for `npm run start:staff:vm` against the shared VM's services (see staff-proxy.mjs).
import { staffProxy } from './staff-proxy.mjs';

export default staffProxy('10.14.140.190');
